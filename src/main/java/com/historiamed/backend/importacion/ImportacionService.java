package com.historiamed.backend.importacion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
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
import com.historiamed.backend.alergia.AlergiaService;
import com.historiamed.backend.antecedente.AntecedenteService;
import com.historiamed.backend.cita.CitaService;
import com.historiamed.backend.documento.AlmacenamientoService;
import com.historiamed.backend.documento.Documento;
import com.historiamed.backend.documento.DocumentoService;
import com.historiamed.backend.documento.FormatoArchivo;
import com.historiamed.backend.extraccion.ExtraccionIaService;
import com.historiamed.backend.extraccion.ExtraccionIaService.PropuestaIa;
import com.historiamed.backend.extraccion.ExtraccionIaService.ResultadoImportacion;
import com.historiamed.backend.importacion.dto.AprobacionRequest;
import com.historiamed.backend.importacion.dto.AprobacionResponse;
import com.historiamed.backend.importacion.dto.ImportacionDetalle;
import com.historiamed.backend.importacion.dto.EspacioResponse;
import com.historiamed.backend.importacion.dto.ImportacionResponse;
import com.historiamed.backend.laboratorio.LaboratorioService;
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

	private static final long BYTES_POR_MB = 1024L * 1024L;

	private static final String ARCHIVO_REPETIDO = "ARCHIVO_REPETIDO";

	private static final String YA_SUBIDO = "Este archivo ya fue subido";

	private static final List<Importacion.Estado> EN_PROCESO = List.of(Importacion.Estado.EN_COLA,
			Importacion.Estado.PROCESANDO, Importacion.Estado.LISTA, Importacion.Estado.ERROR);

	private static final List<Importacion.Estado> DESCARTADAS = List.of(Importacion.Estado.RECHAZADA,
			Importacion.Estado.ELIMINADA, Importacion.Estado.ANULADA);

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

	private final ImportacionContenidoRepository contenidos;

	private final AlergiaService alergiaService;

	private final AntecedenteService antecedenteService;

	private final LaboratorioService laboratorioService;

	private final CitaService citaService;

	/** Tope de la base, por debajo de los 512 MB gratuitos de Neon: al llegar no se aceptan más archivos. */
	@Value("${historiamed.importacion.cuota-mb:450}")
	private long cuotaMb;

	@Transactional
	public ImportacionResponse subir(MultipartFile archivo) {
		if (repository.countByEstadoIn(EN_PROCESO) >= MAXIMO_EN_PROCESO) {
			throw new ReglaNegocioException("Hay " + MAXIMO_EN_PROCESO + " documentos sin aprobar. Revise esos antes de subir más");
		}
		byte[] contenido = leer(archivo);
		if (repository.tamanioBase() + contenido.length > cuotaMb * BYTES_POR_MB) {
			throw new ReglaNegocioException("ESPACIO_AGOTADO",
					"El espacio disponible se agotó. Apruebe o elimine documentos antes de subir más", Map.of());
		}
		if (contenido.length == 0) {
			throw new ReglaNegocioException("El archivo está vacío");
		}
		FormatoArchivo formato = FormatoArchivo.detectar(contenido)
			.orElseThrow(() -> new ReglaNegocioException("Solo se aceptan archivos PDF, JPG o PNG"));
		String huella = DocumentoService.sha256(contenido);
		if (repository.existsBySha256AndEstadoNotIn(huella, DESCARTADAS)) {
			throw new ReglaNegocioException(ARCHIVO_REPETIDO, YA_SUBIDO, Map.of());
		}
		Importacion i = new Importacion();
		i.setNombreOriginal(DocumentoService.nombreSeguro(archivo.getOriginalFilename(), formato));
		i.setContentType(formato.contentType());
		i.setTamanioBytes((long) contenido.length);
		i.setSha256(huella);
		i.setSubidoPor(usuarioService.obtener(UsuarioActual.requerido().id()));
		try {
			repository.saveAndFlush(i);
		}
		catch (DataIntegrityViolationException e) {
			throw new ReglaNegocioException(ARCHIVO_REPETIDO, YA_SUBIDO, Map.of());
		}
		contenidos.save(new ImportacionContenido(i.getId(), contenido));
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
		return new ImportacionDetalle(ImportacionResponse.de(i), propuesta.paciente(), propuesta.datos(), existente(propuesta),
				parecidos(propuesta), Boolean.TRUE.equals(propuesta.variosPacientes()),
				propuesta.datosOmitidos() == null ? 0 : propuesta.datosOmitidos());
	}

	@Transactional
	public Resource archivo(Long id) {
		Importacion i = buscar(id);
		if (!i.abierta()) {
			throw new ReglaNegocioException("La importación ya se cerró: el documento está en la ficha del paciente");
		}
		auditoria.registrar(AccionAuditoria.DESCARGAR, RECURSO, id, null);
		return new ByteArrayResource(contenido(i));
	}

	@Transactional(readOnly = true)
	public Importacion obtener(Long id) {
		return buscar(id);
	}

	/**
	 * Ubica o crea al paciente y registra lo revisado. A un paciente ya registrado no se le cambian los datos personales
	 * (solo se agrega su historia). Si algo indica que se mezclaría o duplicaría a una persona, se exige confirmar.
	 */
	@Transactional
	public AprobacionResponse aprobar(Long id, AprobacionRequest req) {
		Importacion i = bloquear(id);
		if (i.getEstado() != Importacion.Estado.LISTA) {
			throw new ReglaNegocioException("El documento no está listo para aprobar");
		}
		PropuestaIa propuesta = propuesta(i);
		if (Boolean.TRUE.equals(propuesta.variosPacientes()) && !Boolean.TRUE.equals(req.confirmarUnPaciente())) {
			throw new ReglaNegocioException("VARIOS_PACIENTES",
					"El documento parece tener a más de una persona. Súbalos por separado o confirme que es una sola", Map.of());
		}
		PacienteRequest datos = req.paciente();
		Optional<Paciente> existente = req.pacienteId() != null ? Optional.of(pacienteService.obtener(req.pacienteId()))
				: conDocumento(datos.tipoDocumento(), datos.numeroDocumento());
		exigirConfirmacion(req, datos, existente);
		existente.ifPresent(pacienteService::reactivar);
		Paciente paciente = existente.orElseGet(() -> pacienteService.registrar(datos));

		byte[] contenido = contenido(i);
		String clave = i.getClaveAlmacenamiento();
		if (clave == null) {
			clave = almacenamiento.guardar(contenido, extension(contenido));
			i.setClaveAlmacenamiento(clave);
			contenidos.deleteById(id);
		}
		Documento documento = documentoService.registrarImportado(paciente, i.getNombreOriginal(), i.getContentType(),
				i.getTamanioBytes(), clave, i.getSha256(),
				"Importado de una historia anterior. Aprobado por Admisión, sin revisión médica");
		ResultadoImportacion r = extraccionIa.registrarImportada(documento, propuesta.datos(), req.datos(), i.getModelo());

		i.setEstado(Importacion.Estado.APROBADA);
		i.setPacienteId(paciente.getId());
		i.setDocumentoId(documento.getId());
		i.setPacienteNuevo(existente.isEmpty());
		cerrar(i);
		auditoria.registrarSobrePaciente(AccionAuditoria.VALIDAR, RECURSO, id, paciente.getId(),
				"Importación aprobada por Admisión: " + r.aplicados() + " dato(s) registrados, " + r.corregidos()
						+ " corregido(s)" + (existente.isPresent() ? " en un paciente existente" : " en un paciente nuevo"));
		return new AprobacionResponse(paciente.getId(), paciente.getNumeroHc(), existente.isEmpty(), r.aplicados(), r.corregidos());
	}

	private void exigirConfirmacion(AprobacionRequest req, PacienteRequest datos, Optional<Paciente> existente) {
		if (Boolean.TRUE.equals(req.confirmarCoincidencia())) {
			return;
		}
		if (existente.isPresent()) {
			if (!pacienteService.mismaPersona(existente.get(), datos.nombres(), datos.apellidoPaterno())) {
				throw new ReglaNegocioException("COINCIDENCIA_DUDOSA", "El documento ya pertenece a "
						+ existente.get().nombreCompleto() + ". Confirme que es la misma persona", Map.of());
			}
			return;
		}
		if (!pacienteService.parecidos(datos.fechaNacimiento(), datos.apellidoPaterno()).isEmpty()) {
			throw new ReglaNegocioException("POSIBLE_DUPLICADO",
					"Ya hay un paciente con ese apellido y fecha de nacimiento. Elíjalo o confirme que es otra persona", Map.of());
		}
	}

	/** Descarta el documento. Nada pasó a la historia, y queda registrado por qué. */
	@Transactional
	public ImportacionResponse rechazar(Long id, String motivo) {
		Importacion i = bloquear(id);
		if (i.getEstado() != Importacion.Estado.LISTA && i.getEstado() != Importacion.Estado.ERROR) {
			throw new ReglaNegocioException("Este documento no se puede rechazar ahora");
		}
		i.setEstado(Importacion.Estado.RECHAZADA);
		i.setMotivoRechazo(motivo.trim());
		liberar(i);
		cerrar(i);
		auditoria.registrar(AccionAuditoria.VALIDAR, RECURSO, id, "Importación rechazada");
		return ImportacionResponse.de(i);
	}

	/** Quita un archivo subido por error. Como aún no es parte de ninguna historia, se borra y libera espacio. */
	@Transactional
	public ImportacionResponse eliminar(Long id) {
		Importacion i = bloquear(id);
		if (!i.abierta()) {
			throw new ReglaNegocioException("Este documento ya se cerró: si se aprobó por error, anule la importación");
		}
		if (i.getEstado() == Importacion.Estado.PROCESANDO) {
			throw new ReglaNegocioException("La IA está leyendo este documento. Espere un momento");
		}
		i.setEstado(Importacion.Estado.ELIMINADA);
		liberar(i);
		cerrar(i);
		auditoria.registrar(AccionAuditoria.DESACTIVAR, RECURSO, id, "Importación eliminada antes de aprobar");
		return ImportacionResponse.de(i);
	}

	/**
	 * Corrige una aprobación equivocada. La historia no se borra: lo registrado queda inactivo con su motivo y el
	 * documento anulado. Un paciente creado por esta importación, sin otra actividad, también se inactiva.
	 */
	@Transactional
	public ImportacionResponse anular(Long id, String motivo) {
		Importacion i = bloquear(id);
		if (i.getEstado() != Importacion.Estado.APROBADA) {
			throw new ReglaNegocioException("Solo se anula una importación aprobada");
		}
		String razon = "Importación anulada: " + motivo.trim();
		alergiaService.inactivarDeDocumento(i.getDocumentoId(), razon);
		antecedenteService.inactivarDeDocumento(i.getDocumentoId(), razon);
		laboratorioService.inactivarDeDocumento(i.getDocumentoId(), razon);
		documentoService.anularImportado(i.getDocumentoId(), razon);
		boolean sinActividad = i.isPacienteNuevo() && !citaService.tieneCitas(i.getPacienteId())
				&& documentoService.contarVigentes(i.getPacienteId()) == 0;
		if (sinActividad) {
			pacienteService.inactivarImportado(i.getPacienteId());
		}
		i.setEstado(Importacion.Estado.ANULADA);
		i.setMotivoRechazo(motivo.trim());
		cerrar(i);
		auditoria.registrarSobrePaciente(AccionAuditoria.DESACTIVAR, RECURSO, id, i.getPacienteId(), razon);
		return ImportacionResponse.de(i);
	}

	/** Cuántos documentos importados tiene el paciente, para avisar al médico. Solo una cifra. */
	@Transactional(readOnly = true)
	public long contarDelPaciente(Long pacienteId) {
		return repository.countByPacienteIdAndEstado(pacienteId, Importacion.Estado.APROBADA);
	}

	@Transactional(readOnly = true)
	public EspacioResponse espacio() {
		return new EspacioResponse(repository.tamanioBase() / BYTES_POR_MB, cuotaMb);
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

	private void liberar(Importacion i) {
		i.setDatosCifrados(null);
		contenidos.findById(i.getId()).ifPresent(contenidos::delete);
	}

	private byte[] contenido(Importacion i) {
		Optional<ImportacionContenido> pendiente = contenidos.findById(i.getId());
		if (pendiente.isPresent()) {
			return pendiente.get().getContenido();
		}
		try {
			return almacenamiento.obtener(i.getClaveAlmacenamiento()).getContentAsByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException("No se pudo leer el archivo", e);
		}
	}

	private static String extension(byte[] contenido) {
		return FormatoArchivo.detectar(contenido).map(FormatoArchivo::extension).orElse("pdf");
	}

	private List<ImportacionDetalle.PacienteExistente> parecidos(PropuestaIa propuesta) {
		var p = propuesta.paciente();
		if (p == null || existente(propuesta) != null) {
			return List.of();
		}
		return pacienteService.parecidos(fecha(p.fechaNacimiento()), p.apellidoPaterno())
			.stream()
			.map(e -> new ImportacionDetalle.PacienteExistente(e.getId(), e.getNumeroHc(), e.nombreCompleto(),
					pacienteService.mismaPersona(e, p.nombres(), p.apellidoPaterno())))
			.toList();
	}

	private static LocalDate fecha(String valor) {
		try {
			return valor == null ? null : LocalDate.parse(valor.trim());
		}
		catch (DateTimeParseException e) {
			return null;
		}
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

	private Importacion bloquear(Long id) {
		return repository.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Importación", id));
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
