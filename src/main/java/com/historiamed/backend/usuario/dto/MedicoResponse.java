package com.historiamed.backend.usuario.dto;

import com.historiamed.backend.usuario.Usuario;

/**
 * Datos públicos de un médico para elegirlo en una cita (sin datos de cuenta).
 */
public record MedicoResponse(Long id, String nombreCompleto, String cmp) {

	public static MedicoResponse de(Usuario u) {
		return new MedicoResponse(u.getId(), u.getApellidos() + ", " + u.getNombres(), u.getCmp());
	}

}
