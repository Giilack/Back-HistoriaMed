package com.historiamed.backend.extraccion.dto;

import java.time.Instant;
import java.util.List;

import com.historiamed.backend.extraccion.Extraccion;
import com.historiamed.backend.usuario.Usuario;

/**
 * La revisión de un documento con todos sus datos.
 *
 * @param revisadoPor médico que la validó o rechazó (nulo mientras está pendiente)
 */
public record ExtraccionResponse(Long id, Long documentoId, Long pacienteId, Extraccion.Origen origen,
		Extraccion.Estado estado, String motivoRechazo, String creadoPor, Instant creadoEn, String revisadoPor,
		Instant revisadoEn, List<ItemResponse> items) {

	public static ExtraccionResponse de(Extraccion e) {
		return new ExtraccionResponse(e.getId(), e.getDocumento().getId(), e.getPaciente().getId(), e.getOrigen(),
				e.getEstado(), e.getMotivoRechazo(), nombre(e.getCreadoPor()), e.getCreadoEn(),
				nombre(e.getRevisadoPor()), e.getRevisadoEn(), e.getItems().stream().map(ItemResponse::de).toList());
	}

	private static String nombre(Usuario u) {
		return u == null ? null : u.getApellidos() + ", " + u.getNombres();
	}

}
