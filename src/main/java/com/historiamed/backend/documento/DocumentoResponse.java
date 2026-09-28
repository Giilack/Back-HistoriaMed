package com.historiamed.backend.documento;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Metadatos del documento (el contenido se descarga aparte, solo TRIAJE y MEDICO).
 */
public record DocumentoResponse(Long id, Long pacienteId, Long citaId, TipoDocumentoClinico tipo,
		String descripcion, LocalDate fechaDocumento, String nombreOriginal, String contentType, long tamanioBytes,
		Documento.Estado estado, String motivoAnulacion, Long subidoPorId, String subidoPor, Instant creadoEn) {

	public static DocumentoResponse de(Documento d) {
		return new DocumentoResponse(d.getId(), d.getPaciente().getId(),
				d.getCita() == null ? null : d.getCita().getId(), d.getTipo(), d.getDescripcion(),
				d.getFechaDocumento(), d.getNombreOriginal(), d.getContentType(), d.getTamanioBytes(), d.getEstado(),
				d.getMotivoAnulacion(), d.getSubidoPor().getId(),
				d.getSubidoPor().getApellidos() + ", " + d.getSubidoPor().getNombres(), d.getCreadoEn());
	}

}
