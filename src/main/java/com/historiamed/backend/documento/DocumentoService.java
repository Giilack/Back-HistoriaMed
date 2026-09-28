package com.historiamed.backend.documento;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.cita.Cita;
import com.historiamed.backend.cita.CitaService;
import com.historiamed.backend.common.exception.AccesoProhibidoException;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.security.UsuarioActual;
import com.historiamed.backend.common.util.Tiempo;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.PacienteService;
import com.historiamed.backend.usuario.Rol;
import com.historiamed.backend.usuario.UsuarioService;

import lombok.RequiredArgsConstructor;

/**
 * Documentos clínicos (plan.md, sección 5.5, sin IA por ahora). Suben ADMISION, TRIAJE y MEDICO; el contenido
 * solo lo ven TRIAJE y MEDICO (ADMISION ve la lista, para no subir duplicados).
 */
@Service
@RequiredArgsConstructor
public class DocumentoService {

	private static final String RECURSO = "DOCUMENTO";

	private static final int MAXIMO_NOMBRE = 255;

	private final DocumentoRepository repository;

	private final AlmacenamientoService almacenamiento;

	private final PacienteService pacienteService;

	private final CitaService citaService;

	private final UsuarioService usuarioService;

	private final AuditoriaService auditoria;

	/** Archivo descargado: metadatos + contenido. */
	public record Archivo(Documento documento, Resource contenido) {
	}

	@Transactional
	public DocumentoResponse subir(Long pacienteId, MultipartFile archivo, TipoDocumentoClinico tipo,
			String descripcion, LocalDate fechaDocumento, Long citaId) {
		Paciente paciente = pacienteService.obtener(pacienteId);
		if (!paciente.isActivo()) {
			throw new ReglaNegocioException("El paciente " + paciente.getNumeroHc() + " está inactivo");
		}
		Cita cita = null;
		if (citaId != null) {
			cita = citaService.obtenerEntidad(citaId);
			if (!cita.getPaciente().getId().equals(pacienteId)) {
				throw new ReglaNegocioException("La cita indicada no pertenece a este paciente");
			}
		}
		if (fechaDocumento != null && fechaDocumento.isAfter(Tiempo.hoy())) {
			throw new ReglaNegocioException("La fecha del documento no puede ser futura");
		}
		String desc = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
		if (desc != null && desc.length() > 200) {
			throw new ReglaNegocioException("La descripción admite hasta 200 caracteres");
		}

		byte[] contenido = leer(archivo);
		if (contenido.length == 0) {
			throw new ReglaNegocioException("El archivo está vacío");
		}
		FormatoArchivo formato = FormatoArchivo.detectar(contenido)
			.orElseThrow(() -> new ReglaNegocioException(
					"Solo se aceptan archivos PDF, JPG o PNG (se verifica el contenido real, no solo la extensión)"));

		Documento d = new Documento();
		d.setPaciente(paciente);
		d.setCita(cita);
		d.setTipo(tipo);
		d.setDescripcion(desc);
		d.setFechaDocumento(fechaDocumento);
		d.setNombreOriginal(nombreSeguro(archivo.getOriginalFilename(), formato));
		d.setContentType(formato.contentType());
		d.setTamanioBytes((long) contenido.length);
		d.setSha256(sha256(contenido));
		d.setSubidoPor(usuarioService.obtener(UsuarioActual.requerido().id()));
		d.setClaveAlmacenamiento(almacenamiento.guardar(contenido, formato.extension()));
		repository.save(d);
		auditoria.registrarSobrePaciente(AccionAuditoria.CREAR, RECURSO, d.getId(), pacienteId,
				d.getTipo() + ": " + d.getNombreOriginal());
		return DocumentoResponse.de(d);
	}

	@Transactional
	public List<DocumentoResponse> listar(Long pacienteId) {
		pacienteService.obtener(pacienteId);
		List<DocumentoResponse> documentos = repository.findByPacienteIdOrderByCreadoEnDesc(pacienteId)
			.stream()
			.map(DocumentoResponse::de)
			.toList();
		auditoria.registrarSobrePaciente(AccionAuditoria.VER, RECURSO, null, pacienteId, "Lista de documentos");
		return documentos;
	}

	/** Contenido del documento, para verlo o descargarlo. Cada descarga queda en la auditoría. */
	@Transactional
	public Archivo descargar(Long id) {
		Documento d = obtener(id);
		if (d.getEstado() == Documento.Estado.ANULADO) {
			throw new ReglaNegocioException("El documento fue anulado: " + d.getMotivoAnulacion());
		}
		Resource contenido = almacenamiento.obtener(d.getClaveAlmacenamiento());
		auditoria.registrarSobrePaciente(AccionAuditoria.DESCARGAR, RECURSO, id, d.getPaciente().getId(),
				d.getNombreOriginal());
		return new Archivo(d, contenido);
	}

	/**
	 * Anula un documento subido por error (por ejemplo, de otro paciente). Pueden hacerlo quien lo subió o un
	 * médico. El registro queda; el contenido deja de mostrarse.
	 */
	@Transactional
	public DocumentoResponse anular(Long id, String motivo) {
		Documento d = obtener(id);
		UsuarioActual yo = UsuarioActual.requerido();
		boolean esAutor = d.getSubidoPor().getId().equals(yo.id());
		if (!esAutor && !Rol.MEDICO.name().equals(yo.rol())) {
			throw new AccesoProhibidoException("Solo quien subió el documento o un médico pueden anularlo");
		}
		if (d.getEstado() == Documento.Estado.ANULADO) {
			throw new ReglaNegocioException("El documento ya está anulado");
		}
		d.setEstado(Documento.Estado.ANULADO);
		d.setMotivoAnulacion(motivo.trim());
		auditoria.registrarSobrePaciente(AccionAuditoria.DESACTIVAR, RECURSO, id, d.getPaciente().getId(),
				d.getNombreOriginal() + ": " + d.getMotivoAnulacion());
		return DocumentoResponse.de(d);
	}

	private Documento obtener(Long id) {
		return repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Documento", id));
	}

	private static byte[] leer(MultipartFile archivo) {
		try {
			return archivo.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("No se pudo leer el archivo", ex);
		}
	}

	/** Solo el nombre (sin rutas), recortado; si no hay nombre, uno genérico con la extensión real. */
	static String nombreSeguro(String original, FormatoArchivo formato) {
		// No se usa Path.of: en Windows falla con caracteres como '"' que un navegador sí puede enviar
		String nombre = original == null ? "" : original.substring(original.replace('\\', '/').lastIndexOf('/') + 1);
		nombre = nombre.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").trim();
		if (nombre.isEmpty()) {
			nombre = "documento." + formato.extension();
		}
		return nombre.length() > MAXIMO_NOMBRE ? nombre.substring(nombre.length() - MAXIMO_NOMBRE) : nombre;
	}

	static String sha256(byte[] contenido) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
