package com.historiamed.backend.extraccion.dto;

import java.time.Instant;

import com.historiamed.backend.extraccion.Extraccion;

/**
 * Estado de la revisión de un documento, sin los datos: para mostrarlo en la lista de documentos del paciente.
 */
public record ExtraccionResumenResponse(Long id, Long documentoId, Extraccion.Origen origen, Extraccion.Estado estado,
		int totalItems, Instant creadoEn) {

	public static ExtraccionResumenResponse de(Extraccion e) {
		return new ExtraccionResumenResponse(e.getId(), e.getDocumento().getId(), e.getOrigen(), e.getEstado(),
				e.getItems().size(), e.getCreadoEn());
	}

}
