package com.historiamed.backend.importacion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.security.CifradoDatos;
import com.historiamed.backend.common.security.UsuarioActual;
import com.historiamed.backend.documento.AlmacenamientoService;
import com.historiamed.backend.documento.Documento;
import com.historiamed.backend.documento.DocumentoService;
import com.historiamed.backend.documento.FormatoArchivo;
import com.historiamed.backend.extraccion.ExtraccionIaService;
import com.historiamed.backend.extraccion.ExtraccionIaService.PropuestaIa;
import com.historiamed.backend.importacion.dto.AprobacionRequest;
import com.historiamed.backend.importacion.dto.AprobacionResponse;
import com.historiamed.backend.importacion.dto.ImportacionDetalle;
import com.historiamed.backend.importacion.dto.ImportacionResponse;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.PacienteService;
import com.historiamed.backend.paciente.TipoDocumento;
import com.historiamed.backend.paciente.dto.PacienteRequest;
import com.historiamed.backend.usuario.UsuarioService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Importación masiva de historias en PDF. Cada archivo entra por su propia petición y queda en una cola en la base: la
 * IA los lee de uno en uno, sin mantener varios archivos en memoria. Nada entra a la historia hasta que Admisión lo
 * aprueba (plan.md, sección 5.5.1).
 */
@Service
@RequiredArgsConstructor
public class ImportacionService {

	private static final String RECURSO = "IMPORTACION";

	/** Tope de archivos sin aprobar: protege el almacenamiento de la capa gratuita (Neon, 0,5 GB). */
	static final int MAXIMO_EN_PROCESO = 100;

	private static final List<Importacion.Estado> EN_PROCESO = List.of(Importacion.Estado.EN_COLA,
			Importacion.Estado.PROCESANDO, Importacion.Estado.LISTA, Importacion.Estado.ERROR);

	private final ImportacionRepository repository;

	private final AlmacenamientoService almacenamiento;

	private final DocumentoService documentoService;

	private final PacienteService pacienteService;

	private final ExtraccionIaService extraccionIa;

	private final UsuarioService usuarioService;

	private final AuditoriaService auditoria;

	private final CifradoDatos cifrado;

	private final JsonMapper json;

	private final ColaImportacion cola;

	@Transactional
	public ImportacionResponse subir(MultipartFile archivo) {
		if (repository.countByEstadoIn(EN_PROCESO) >= MAXIMO_EN_PROCESO) {
			throw new ReglaNegocioException("Hay " + MAXIMO_EN_PROCESO + " documentos sin aprobar. Revise esos antes de subir más");
		}
		byte[] contenido = leer(archivo);
		if (contenido.length == 0) {
			throw new ReglaNegocioException("El archivo está vacío");
		}
		FormatoArchivo formato = FormatoArchivo.detectar(contenido)
			.orElseThrow(() -> new ReglaNegocioException("Solo se aceptan archivos PDF, JPG o PNG"));
		String huella = DocumentoService.sha256(contenido);
		if (repository.existsBySha256AndEstadoNot(huella, Importacion.Estado.RECHAZADA)) {
			throw new ReglaNegocioException("ARCHIVO_REPETIDO", "Este archivo ya fue subido", Map.of());
		}
		Importacion i = new Importacion();
		i.setNombreOriginal(DocumentoService.nombreSeguro(archivo.getOriginalFilename(), formato));
		i.setContentType(formato.contentType());
		i.setTamanioBytes((long) contenido.length);
		i.setSha256(huella);
		i.setClaveAlmacenamiento(almacenamiento.guardar(contenido, formato.extension()));
		i.setSubidoPor(usuarioService.obtener(UsuarioActual.requerido().id()));
		try {
			repository.saveAndFlush(i);
		}
		catch (DataIntegrityViolationException e) {
			throw new ReglaNegocioException("ARCHIVO_REPETIDO", "Este archivo ya fue subido", Map.of());
		}
		auditoria.registrar(AccionAuditoria.CREAR, RECURSO, i.getId(), "Documento en la cola de importación");
		cola.avisar();
		return ImportacionResponse.de(i);
	}

	@Transactional(readOnly = true)
	public List<ImportacionResponse> listar() {
		return repository.findTop200ByOrderByIdDesc().stream().map(ImportacionResponse::de).toList();
	}

	/** Abre la revisión. Admisión ve aquí el contenido clínico del documento: por eso cada apertura se audita. */
	@Transactional
	public ImportacionDetalle ver(Long id) {
		Importacion i = buscar(id);
		if (i.getEstado() != Importacion.Estado.LISTA) {
			throw new ReglaNegocioException("El documento no está listo para revisar");
		}
		PropuestaIa propuesta = propuesta(i);
		auditoria.registrar(AccionAuditoria.VER, RECURSO, id, "Revisión de importación");
		return new ImportacionDetalle(ImportacionResponse.de(i), propuesta.paciente(), propuesta.datos(), existente(propuesta));
	}

	@Transactional
	public Resource archivo(Long id) {
		Importacion i = buscar(id);
		if (!i.abierta()) {
			throw new ReglaNegocioException("La importación ya se cerró: el documento está en la ficha del paciente");
		}
		auditoria.registrar(AccionAuditoria.DESCARGAR, RECURSO, id, null);
		return almacenamiento.obtener(i.getClaveAlmacenamiento());
	}

	@Transactional(readOnly = true)
	public Importacion obtener(Long id) {
		return buscar(id);
	}

	/**
	 * Ubica o crea al paciente y registra lo aceptado. A un paciente ya registrado no se le cambian los datos personales
	 * (solo se agrega su historia); si el nombre no coincide, se exige confirmar para no mezclar dos historias.
	 */
	@Transactional
	public AprobacionResponse aprobar(Long id, AprobacionRequest req) {
		Importacion i = buscar(id);
		if (i.getEstado() != Importacion.Estado.LISTA) {
			throw new ReglaNegocioException("El documento no está listo para aprobar");
		}
		PropuestaIa propuesta = propuesta(i);
		PacienteRequest datos = req.paciente();
		Optional<Paciente> existente = conDocumento(datos.tipoDocumento(), datos.numeroDocumento());
		if (existente.isPresent() && !req.confirmarCoincidencia()
				&& !pacienteService.mismaPersona(existente.get(), datos.nombres(), datos.apellidoPaterno())) {
			throw new ReglaNegocioException("COINCIDENCIA_DUDOSA", "El documento ya pertenece a "
					+ existente.get().nombreCompleto() + ". Confirme que es la misma persona", Map.of());
		}
		Paciente paciente = existente.orElseGet(() -> pacienteService.registrar(datos));

		Documento documento = documentoService.registrarImportado(paciente, i.getNombreOriginal(), i.getContentType(),
				i.getTamanioBytes(), i.getClaveAlmacenamiento(), i.getSha256(),
				"Importado de una historia anterior. Aprobado por Admisión, sin revisión médica");
		int aplicados = extraccionIa.registrarImportada(documento, propuesta.datos(), req.aceptados(), i.getModelo());

		i.setEstado(Importacion.Estado.APROBADA);
		i.setPacienteId(paciente.getId());
		i.setDocumentoId(documento.getId());
		cerrar(i);
		auditoria.registrarSobrePaciente(AccionAuditoria.VALIDAR, RECURSO, id, paciente.getId(),
				"Importación aprobada por Admisión: " + aplicados + " dato(s) registrados"
						+ (existente.isPresent() ? " en un paciente existente" : " en un paciente nuevo"));
		return new AprobacionResponse(paciente.getId(), paciente.getNumeroHc(), existente.isEmpty(), aplicados);
	}

	@Transactional
	public ImportacionResponse rechazar(Long id, String motivo) {
		Importacion i = buscar(id);
		if (i.getEstado() != Importacion.Estado.LISTA && i.getEstado() != Importacion.Estado.ERROR) {
			throw new ReglaNegocioException("Este documento no se puede rechazar ahora");
		}
		i.setEstado(Importacion.Estado.RECHAZADA);
		i.setMotivoRechazo(motivo.trim());
		cerrar(i);
		auditoria.registrar(AccionAuditoria.VALIDAR, RECURSO, id, "Importación rechazada");
		return ImportacionResponse.de(i);
	}

	@Transactional
	public ImportacionResponse reintentar(Long id) {
		Importacion i = buscar(id);
		if (i.getEstado() != Importacion.Estado.ERROR) {
			throw new ReglaNegocioException("Solo se reintenta un documento con error");
		}
		i.setEstado(Importacion.Estado.EN_COLA);
		i.setError(null);
		i.setIntentos(0);
		cola.avisar();
		return ImportacionResponse.de(i);
	}

	// ------------------------------------------------------------------------------------------------------------

	private void cerrar(Importacion i) {
		i.setDatosCifrados(null);
		i.setRevisadoPor(usuarioService.obtener(UsuarioActual.requerido().id()));
		i.setRevisadoEn(Instant.now());
	}

	private ImportacionDetalle.PacienteExistente existente(PropuestaIa propuesta) {
		var p = propuesta.paciente();
		if (p == null) {
			return null;
		}
		TipoDocumento tipo = tipo(p.tipoDocumento());
		return conDocumento(tipo, p.numeroDocumento())
			.map(e -> new ImportacionDetalle.PacienteExistente(e.getId(), e.getNumeroHc(), e.nombreCompleto(),
					pacienteService.mismaPersona(e, p.nombres(), p.apellidoPaterno())))
			.orElse(null);
	}

	private Optional<Paciente> conDocumento(TipoDocumento tipo, String numero) {
		return tipo == null || tipo == TipoDocumento.SIN_DOCUMENTO || numero == null || numero.isBlank() ? Optional.empty()
				: pacienteService.porDocumento(tipo, numero);
	}

	private static TipoDocumento tipo(String valor) {
		if (valor == null) {
			return TipoDocumento.DNI;
		}
		try {
			return TipoDocumento.valueOf(valor.trim().toUpperCase());
		}
		catch (IllegalArgumentException e) {
			return TipoDocumento.DNI;
		}
	}

	private PropuestaIa propuesta(Importacion i) {
		return json.readValue(cifrado.descifrar(i.getDatosCifrados()), PropuestaIa.class);
	}

	private Importacion buscar(Long id) {
		return repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Importación", id));
	}

	private static byte[] leer(MultipartFile archivo) {
		try {
			return archivo.getBytes();
		}
		catch (IOException e) {
			throw new UncheckedIOException("No se pudo leer el archivo", e);
		}
	}

}
