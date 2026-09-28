package com.historiamed.backend.triaje.dto;

import com.historiamed.backend.triaje.Alerta;
import com.historiamed.backend.triaje.Severidad;

public record AlertaResponse(String codigo, Severidad severidad, String mensaje) {

	public static AlertaResponse de(Alerta a) {
		return new AlertaResponse(a.getCodigo(), a.getSeveridad(), a.getMensaje());
	}

}
