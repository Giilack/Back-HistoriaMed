package com.historiamed.backend.importacion;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.historiamed.backend.common.security.CifradoDatos;
import com.historiamed.backend.documento.AlmacenamientoService;
import com.historiamed.backend.extraccion.ExtraccionIaService;
import com.historiamed.backend.extraccion.ExtraccionIaService.PropuestaIa;
import com.historiamed.backend.ia.ErrorIaException;
import com.historiamed.backend.ia.LimiteIaException;
import com.historiamed.backend.ia.ProveedorIA;

import tools.jackson.databind.json.JsonMapper;

/**
 * Procesa la cola de uno en uno: respeta el límite de la capa gratuita de Gemini y evita tener varios archivos en
 * memoria en un servidor de 512 MB. La cola vive en la base, así que un reinicio no pierde trabajo. Sin documentos
 * pendientes no consulta la base, para que Neon pueda apagarse.
 */
@Component
public class ColaImportacion {

	private static final Logger log = LoggerFactory.getLogger(ColaImportacion.class);

	static final int MAXIMO_INTENTOS = 5;

	private final ImportacionRepository repository;

	private final ImportacionContenidoRepository contenidos;

	private final AlmacenamientoService almacenamiento;

	private final ExtraccionIaService extraccionIa;

	private final ProveedorIA proveedor;

	private final CifradoDatos cifrado;

	private final JsonMapper json;

	private final TransactionTemplate transaccion;

	private final long pausaTrasLimiteMs;

	private final AtomicBoolean hayTrabajo = new AtomicBoolean(true);

	private volatile long noAntesDe;

	public ColaImportacion(ImportacionRepository repository, ImportacionContenidoRepository contenidos,
			AlmacenamientoService almacenamiento,
			ExtraccionIaService extraccionIa, ProveedorIA proveedor, CifradoDatos cifrado, JsonMapper json,
			PlatformTransactionManager transacciones,
			@Value("${historiamed.importacion.pausa-tras-limite-ms:60000}") long pausaTrasLimiteMs) {
		this.repository = repository;
		this.contenidos = contenidos;
		this.almacenamiento = almacenamiento;
		this.extraccionIa = extraccionIa;
		this.proveedor = proveedor;
		this.cifrado = cifrado;
		this.json = json;
		this.transaccion = new TransactionTemplate(transacciones);
		this.pausaTrasLimiteMs = pausaTrasLimiteMs;
	}

	/** Se avisa al confirmar la transacción: antes, la cola no vería el documento y se dormiría sin procesarlo. */
	public void avisar() {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			hayTrabajo.set(true);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				hayTrabajo.set(true);
			}
		});
	}

	/** Lo que quedó a medias por un reinicio vuelve a la cola. */
	@EventListener(ApplicationReadyEvent.class)
	public void recuperar() {
		transaccion.executeWithoutResult(
				s -> repository.devolverACola(Importacion.Estado.PROCESANDO, Importacion.Estado.EN_COLA));
	}

	private record Trabajo(Long id, String clave, String contentType) {
	}

	@Scheduled(fixedDelayString = "${historiamed.importacion.pausa-ms:6000}")
	public void procesarSiguiente() {
		// El aviso se baja antes de consultar: si llega otro documento durante la consulta, el aviso queda encendido
		if (!proveedor.disponible() || System.currentTimeMillis() < noAntesDe || !hayTrabajo.getAndSet(false)) {
			return;
		}
		Trabajo trabajo = transaccion.execute(s -> tomar());
		if (trabajo != null) {
			hayTrabajo.set(true);
			procesar(trabajo);
		}
	}

	/** El siguiente documento de la cola; null si está vacía. */
	private Trabajo tomar() {
		return repository.findFirstByEstadoOrderById(Importacion.Estado.EN_COLA).map(i -> {
			i.setEstado(Importacion.Estado.PROCESANDO);
			i.setIntentos(i.getIntentos() + 1);
			i.setModelo(proveedor.modelo());
			return new Trabajo(i.getId(), i.getClaveAlmacenamiento(), i.getContentType());
		}).orElse(null);
	}

	private void procesar(Trabajo t) {
		try {
			byte[] contenido = contenidos.findById(t.id()).map(ImportacionContenido::getContenido).orElse(null);
			if (contenido == null) {
				contenido = leer(almacenamiento.obtener(t.clave()));
			}
			PropuestaIa propuesta = extraccionIa.proponerImportacion(contenido, t.contentType());
			String cifrados = cifrado.cifrar(json.writeValueAsString(propuesta));
			actualizar(t.id(), i -> {
				i.setDatosCifrados(cifrados);
				i.setEstado(Importacion.Estado.LISTA);
				i.setError(null);
			});
		}
		catch (LimiteIaException e) {
			noAntesDe = System.currentTimeMillis() + pausaTrasLimiteMs;
			actualizar(t.id(), i -> {
				boolean agotado = i.getIntentos() >= MAXIMO_INTENTOS;
				i.setEstado(agotado ? Importacion.Estado.ERROR : Importacion.Estado.EN_COLA);
				i.setError(agotado ? e.getMessage() : null);
			});
		}
		catch (ErrorIaException e) {
			fallar(t.id(), e.getMessage());
		}
		catch (RuntimeException | java.io.IOException e) {
			log.warn("Falló la importación {}: {}", t.id(), e.getClass().getSimpleName());
			fallar(t.id(), "La IA no pudo procesar el documento. Puede reintentar");
		}
	}

	private void fallar(Long id, String mensaje) {
		actualizar(id, i -> {
			i.setEstado(Importacion.Estado.ERROR);
			i.setError(mensaje.length() > 500 ? mensaje.substring(0, 500) : mensaje);
		});
	}

	private void actualizar(Long id, java.util.function.Consumer<Importacion> cambio) {
		transaccion.executeWithoutResult(s -> repository.findById(id)
			.filter(i -> i.getEstado() == Importacion.Estado.PROCESANDO)
			.ifPresent(cambio));
	}

	private static byte[] leer(Resource recurso) throws java.io.IOException {
		return recurso.getContentAsByteArray();
	}

}
