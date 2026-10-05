package com.historiamed.backend.extraccion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.historiamed.backend.alergia.GravedadAlergia;
import com.historiamed.backend.alergia.TipoAlergia;
import com.historiamed.backend.antecedente.TipoAntecedente;
import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.catalogo.CatalogoService;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.security.UsuarioActual;
import com.historiamed.backend.common.util.Tiempo;
import com.historiamed.backend.documento.Documento;
import com.historiamed.backend.documento.DocumentoService;
import com.historiamed.backend.extraccion.ExtraccionItem.Categoria;
import com.historiamed.backend.extraccion.dto.ExtraccionResponse;
import com.historiamed.backend.extraccion.dto.ItemRequest;
import com.historiamed.backend.ia.ConfiguracionIa;
import com.historiamed.backend.ia.ErrorIaException;
import com.historiamed.backend.ia.ProveedorIA;
import com.historiamed.backend.usuario.Usuario;
import com.historiamed.backend.usuario.UsuarioService;

import tools.jackson.databind.json.JsonMapper;

/**
 * Extracción con IA (plan.md, sección 5.5, fase 11). La IA lee el documento y propone sus datos en la misma revisión
 * que se llena a mano; desde ahí sigue el flujo de siempre: se corrigen y el médico valida (principio P2).
 * <p>
 * La consulta tarda de segundos a un minuto, así que corre en segundo plano: {@link #solicitar} responde al
 * instante con la revisión en PROCESANDO y la pantalla consulta {@link #estadoIa} hasta que termina.
 */
@Service
public class ExtraccionIaService {

	private static final Logger log = LoggerFactory.getLogger(ExtraccionIaService.class);

	private static final String RECURSO = "EXTRACCION";

	/** Tope de datos que se aceptan de una respuesta, por si la IA devuelve una lista desmedida. */
	static final int MAXIMO_DATOS = 60;

	private final ExtraccionRepository repository;

	private final ExtraccionService extraccionService;

	private final DocumentoService documentoService;

	private final CatalogoService catalogoService;

	private final UsuarioService usuarioService;

	private final AuditoriaService auditoria;

	private final ProveedorIA proveedor;

	private final JsonMapper json;

	private final TaskExecutor ejecutor;

	private final TransactionTemplate transaccion;

	public ExtraccionIaService(ExtraccionRepository repository, ExtraccionService extraccionService,
			DocumentoService documentoService, CatalogoService catalogoService, UsuarioService usuarioService,
			AuditoriaService auditoria, ProveedorIA proveedor, JsonMapper json,
			@Qualifier(ConfiguracionIa.EJECUTOR) TaskExecutor ejecutor, PlatformTransactionManager transacciones) {
		this.repository = repository;
		this.extraccionService = extraccionService;
		this.documentoService = documentoService;
		this.catalogoService = catalogoService;
		this.usuarioService = usuarioService;
		this.auditoria = auditoria;
		this.proveedor = proveedor;
		this.json = json;
		this.ejecutor = ejecutor;
		this.transaccion = new TransactionTemplate(transacciones);
	}

	/** Estado de la IA en una revisión, para que la pantalla lo consulte mientras espera. No se audita: no muestra datos clínicos. */
	public record EstadoIa(Extraccion.EstadoIa iaEstado, String iaError, int totalItems) {
	}

	/**
	 * Envía el documento a la IA. Solo en una revisión pendiente y vacía: la IA propone, no reemplaza lo que ya
	 * escribió una persona.
	 */
	@Transactional
	public ExtraccionResponse solicitar(Long id) {
		Extraccion e = repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Revisión", id));
		if (!proveedor.disponible()) {
			throw new ReglaNegocioException("La extracción con IA no está configurada en este servidor");
		}
		if (!e.estaPendiente()) {
			throw new ReglaNegocioException("La revisión ya fue cerrada");
		}
		if (e.iaProcesando()) {
			throw new ReglaNegocioException("La IA ya está leyendo este documento");
		}
		if (!e.getItems().isEmpty()) {
			throw new ReglaNegocioException(
					"La revisión ya tiene datos. La IA solo se usa en una revisión vacía; quite los datos o continúe a mano");
		}
		if (e.getDocumento().getEstado() == Documento.Estado.ANULADO) {
			throw new ReglaNegocioException("El documento fue anulado");
		}

		e.setIaEstado(Extraccion.EstadoIa.PROCESANDO);
		e.setIaModelo(proveedor.modelo());
		e.setIaError(null);
		e.setIaSolicitadaEn(Instant.now());
		e.setIaTerminadaEn(null);
		Long usuarioId = UsuarioActual.requerido().id();
		auditoria.registrarSobrePaciente(AccionAuditoria.CONSULTA_IA, RECURSO, id, e.getPaciente().getId(),
				"Documento enviado a " + proveedor.modelo());

		// La consulta empieza cuando el estado PROCESANDO ya está guardado
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				ejecutor.execute(() -> procesar(id, usuarioId));
			}
		});
		return ExtraccionResponse.de(e);
	}

	@Transactional(readOnly = true)
	public EstadoIa estadoIa(Long id) {
		Extraccion e = repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Revisión", id));
		return new EstadoIa(e.getIaEstado(), e.getIaError(), e.getItems().size());
	}

	/** Trabajo en segundo plano: leer el archivo, consultar a la IA y guardar los datos propuestos. */
	void procesar(Long id, Long usuarioId) {
		try {
			Lectura lectura = transaccion.execute(s -> leer(id));
			String respuesta = proveedor.extraerJson(lectura.contenido(), lectura.mimeType(), lectura.instrucciones(),
					ESQUEMA);
			List<ItemRequest> datos = interpretar(respuesta);
			transaccion.executeWithoutResult(s -> guardar(id, usuarioId, datos));
		}
		catch (RuntimeException ex) {
			log.warn("La extracción con IA de la revisión {} falló: {}", id, ex.getMessage());
			String mensaje = ex instanceof ErrorIaException ? ex.getMessage()
					: "La IA no pudo procesar el documento. Intente de nuevo o llene los datos a mano";
			transaccion.executeWithoutResult(s -> marcarError(id, mensaje));
		}
	}

	private record Lectura(byte[] contenido, String mimeType, String instrucciones) {
	}

	private Lectura leer(Long id) {
		Extraccion e = repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Revisión", id));
		Documento d = e.getDocumento();
		return new Lectura(documentoService.leerContenido(d), d.getContentType(), instrucciones());
	}

	private void guardar(Long id, Long usuarioId, List<ItemRequest> datos) {
		Extraccion e = repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Revisión", id));
		if (!e.iaProcesando() || !e.estaPendiente()) {
			return;
		}
		Usuario quien = usuarioService.obtener(usuarioId);
		for (ItemRequest dato : datos) {
			ExtraccionItem item = new ExtraccionItem();
			item.setExtraccion(e);
			item.setCreadoPor(quien);
			item.setPropuestoPorIa(true);
			if (aplicar(item, dato)) {
				e.getItems().add(item);
			}
		}
		e.setOrigen(Extraccion.Origen.IA);
		e.setIaEstado(Extraccion.EstadoIa.COMPLETADA);
		e.setIaTerminadaEn(Instant.now());
	}

	/**
	 * Aplica las mismas reglas que al llenar a mano. Si el dato no las cumple (por ejemplo, un código CIE-10 que no
	 * está en el catálogo), no se pierde: se guarda como "otra información" para que la persona decida.
	 */
	private boolean aplicar(ExtraccionItem item, ItemRequest dato) {
		try {
			extraccionService.aplicarDatos(item, dato);
			return true;
		}
		catch (ReglaNegocioException | RecursoNoEncontradoException ex) {
			String texto = comoTexto(dato);
			if (texto.isBlank()) {
				return false;
			}
			extraccionService.aplicarDatos(item, new ItemRequest(Categoria.OTRO, recortar(texto, 300), null,
					dato.fecha(), null, null, null, null, null, null, null, null, dato.fragmentoOrigen(), dato.pagina()));
			return true;
		}
	}

	private void marcarError(Long id, String mensaje) {
		repository.findById(id).filter(Extraccion::iaProcesando).ifPresent(e -> {
			e.setIaEstado(Extraccion.EstadoIa.ERROR);
			e.setIaError(recortar(mensaje, 500));
			e.setIaTerminadaEn(Instant.now());
		});
	}

	/**
	 * Si el servidor se reinició mientras la IA trabajaba (en la capa gratuita, el servidor se duerme), esa consulta
	 * se perdió: se marca como error para que se pueda reintentar.
	 */
	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void recuperarInterrumpidas() {
		int n = repository.marcarIaInterrumpida("El servidor se reinició mientras la IA trabajaba. Intente de nuevo",
				Instant.now());
		if (n > 0) {
			log.info("{} extracción(es) con IA interrumpidas por un reinicio quedaron para reintentar", n);
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// Lo que se le pide a la IA
	// ------------------------------------------------------------------------------------------------------------

	/** Las instrucciones, con los catálogos: la IA elige códigos de ahí en lugar de inventarlos. */
	String instrucciones() {
		String cie10 = catalogoService.todosCie10()
			.stream()
			.map(c -> c.getCodigo() + " — " + c.getDescripcion())
			.collect(Collectors.joining("\n"));
		String medicamentos = catalogoService.medicamentosActivos()
			.stream()
			.map(m -> m.getId() + " — " + m.descripcion())
			.collect(Collectors.joining("\n"));
		return """
				Eres un asistente que extrae datos clínicos de un documento médico para que un médico los revise antes
				de registrarlos. Responde solo con el JSON pedido, en español.

				Reglas:
				1. Extrae solo lo que está escrito en el documento. No deduzcas, no completes ni inventes.
				2. No copies datos que identifiquen al paciente (nombre, documento de identidad, dirección, teléfono,
				   correo, número de historia clínica) en ningún campo, tampoco en fragmentoOrigen.
				3. Categorías:
				   - ALERGIA: sustancia en descripcion, tipoAlergia (MEDICAMENTO, ALIMENTO, AMBIENTAL u OTRO), gravedad
				     (LEVE, MODERADA o SEVERA; si no se indica, MODERADA) y la reacción en detalle.
				   - DIAGNOSTICO: solo si su código está en la lista CIE-10 de abajo; pon el código en cieCodigo. Si el
				     diagnóstico no está en la lista, usa ANTECEDENTE con tipoAntecedente PERSONAL y escribe el
				     diagnóstico (y su código, si figura) en descripcion.
				   - MEDICAMENTO: solo si está en la lista de medicamentos de abajo; pon su número en medicamentoId y la
				     dosis o pauta en detalle. Si no está en la lista, usa OTRO con el nombre y la dosis.
				   - LABORATORIO: un dato por examen o analito. descripcion = nombre del examen; valor = el resultado tal
				     como aparece (puede ser "Negativo" o "<0.5"); unidad y rangoReferencia si figuran.
				   - ANTECEDENTE: antecedentes personales, familiares o quirúrgicos, con tipoAntecedente PERSONAL,
				     FAMILIAR, QUIRURGICO u OTRO.
				   - OTRO: hallazgos clínicos importantes que no encajen en las anteriores (como máximo 5).
				4. fecha: la del dato (por ejemplo, la del examen) en formato AAAA-MM-DD, solo si figura en el documento.
				5. fragmentoOrigen: el texto exacto del documento del que sale el dato (máximo 200 caracteres), y pagina
				   (empezando en 1).
				6. Si el documento no es clínico o no se puede leer, devuelve la lista vacía.

				Lista CIE-10 permitida (código — descripción):
				%s

				Lista de medicamentos permitida (número — medicamento):
				%s
				""".formatted(cie10, medicamentos);
	}

	/** Estructura de la respuesta (formato de esquema de la API de Gemini). */
	static final Map<String, Object> ESQUEMA = esquema();

	private static Map<String, Object> esquema() {
		Map<String, Object> props = new LinkedHashMap<>();
		props.put("categoria", enumerado(Categoria.values()));
		props.put("descripcion", Map.of("type", "STRING"));
		props.put("detalle", texto());
		props.put("fecha", texto());
		props.put("tipoAlergia", opcional(enumerado(TipoAlergia.values())));
		props.put("gravedad", opcional(enumerado(GravedadAlergia.values())));
		props.put("cieCodigo", texto());
		props.put("medicamentoId", Map.of("type", "INTEGER", "nullable", true));
		props.put("valor", texto());
		props.put("unidad", texto());
		props.put("rangoReferencia", texto());
		props.put("tipoAntecedente", opcional(enumerado(new TipoAntecedente[] { TipoAntecedente.PERSONAL,
				TipoAntecedente.FAMILIAR, TipoAntecedente.QUIRURGICO, TipoAntecedente.OTRO })));
		props.put("fragmentoOrigen", texto());
		props.put("pagina", Map.of("type", "INTEGER", "nullable", true));
		Map<String, Object> dato = Map.of("type", "OBJECT", "properties", props, "required",
				List.of("categoria", "descripcion"));
		return Map.of("type", "OBJECT", "properties", Map.of("datos", Map.of("type", "ARRAY", "items", dato)),
				"required", List.of("datos"));
	}

	private static Map<String, Object> texto() {
		return Map.of("type", "STRING", "nullable", true);
	}

	private static Map<String, Object> enumerado(Enum<?>[] valores) {
		List<String> nombres = new ArrayList<>();
		for (Enum<?> v : valores) {
			nombres.add(v.name());
		}
		return Map.of("type", "STRING", "enum", nombres);
	}

	private static Map<String, Object> opcional(Map<String, Object> esquema) {
		Map<String, Object> copia = new LinkedHashMap<>(esquema);
		copia.put("nullable", true);
		return copia;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Lo que devuelve la IA
	// ------------------------------------------------------------------------------------------------------------

	/** Un dato tal como lo devuelve la IA: todo como texto, para no fallar si un campo viene mal formado. */
	record DatoIa(String categoria, String descripcion, String detalle, String fecha, String tipoAlergia,
			String gravedad, String cieCodigo, Long medicamentoId, String valor, String unidad, String rangoReferencia,
			String tipoAntecedente, String fragmentoOrigen, Integer pagina) {
	}

	record RespuestaIa(List<DatoIa> datos) {
	}

	/** Convierte el JSON de la IA en datos con el formato del llenado manual, descartando lo que no se entiende. */
	List<ItemRequest> interpretar(String respuesta) {
		RespuestaIa r;
		try {
			r = json.readValue(respuesta, RespuestaIa.class);
		}
		catch (RuntimeException ex) {
			throw new ErrorIaException("La respuesta de la IA no tiene el formato esperado", ex);
		}
		if (r == null || r.datos() == null) {
			return List.of();
		}
		return r.datos()
			.stream()
			.limit(MAXIMO_DATOS)
			.map(ExtraccionIaService::comoItem)
			.filter(i -> i != null)
			.toList();
	}

	private static ItemRequest comoItem(DatoIa d) {
		Categoria categoria = enumerado(Categoria.class, d.categoria());
		if (categoria == null) {
			return null;
		}
		return new ItemRequest(categoria, recortar(d.descripcion(), 300), recortar(d.detalle(), 300), fecha(d.fecha()),
				enumerado(TipoAlergia.class, d.tipoAlergia()), enumerado(GravedadAlergia.class, d.gravedad()),
				recortar(d.cieCodigo(), 8), d.medicamentoId(), recortar(d.valor(), 60), recortar(d.unidad(), 30),
				recortar(d.rangoReferencia(), 60), enumerado(TipoAntecedente.class, d.tipoAntecedente()),
				recortar(d.fragmentoOrigen(), 500), d.pagina() != null && d.pagina() >= 1 ? d.pagina() : null);
	}

	private static <E extends Enum<E>> E enumerado(Class<E> tipo, String valor) {
		if (valor == null) {
			return null;
		}
		try {
			return Enum.valueOf(tipo, valor.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/** Fecha AAAA-MM-DD que no sea futura; si no se entiende, se deja vacía. */
	private static LocalDate fecha(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		try {
			LocalDate f = LocalDate.parse(valor.trim());
			return f.isAfter(Tiempo.hoy()) ? null : f;
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	private static String comoTexto(ItemRequest d) {
		StringBuilder s = new StringBuilder();
		for (String parte : new String[] { d.descripcion(), d.valor(), d.unidad(), d.detalle() }) {
			if (parte != null && !parte.isBlank()) {
				s.append(s.isEmpty() ? "" : " · ").append(parte.trim());
			}
		}
		if (d.cieCodigo() != null && !d.cieCodigo().isBlank()) {
			s.append(" (CIE-10 ").append(d.cieCodigo().trim()).append(")");
		}
		return s.toString();
	}

	private static String recortar(String texto, int maximo) {
		if (texto == null) {
			return null;
		}
		String t = texto.strip();
		return t.length() <= maximo ? t : t.substring(0, maximo);
	}

}
