package com.historiamed.backend.consultorio.dto;

import com.historiamed.backend.consultorio.Consultorio;

public record ConsultorioResponse(Long id, String nombre, String especialidad, boolean activo) {

	public static ConsultorioResponse de(Consultorio c) {
		return new ConsultorioResponse(c.getId(), c.getNombre(), c.getEspecialidad(), c.isActivo());
	}

}
