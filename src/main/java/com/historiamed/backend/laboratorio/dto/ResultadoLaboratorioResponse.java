package com.historiamed.backend.laboratorio.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.historiamed.backend.laboratorio.ResultadoLaboratorio;

/**
 * @param documentoId documento del que salió el dato (nulo si se registró directamente)
 */
public record ResultadoLaboratorioResponse(Long id, String examen, String valor, String unidad,
		String rangoReferencia, LocalDate fecha, Long documentoId, boolean activo, String motivoInactivacion,
		String registradoPor, Instant creadoEn) {

	public static ResultadoLaboratorioResponse de(ResultadoLaboratorio r) {
		return new ResultadoLaboratorioResponse(r.getId(), r.getExamen(), r.getValor(), r.getUnidad(),
				r.getRangoReferencia(), r.getFecha(), r.getDocumento() == null ? null : r.getDocumento().getId(),
				r.isActivo(), r.getMotivoInactivacion(),
				r.getRegistradoPor().getApellidos() + ", " + r.getRegistradoPor().getNombres(), r.getCreadoEn());
	}

}
