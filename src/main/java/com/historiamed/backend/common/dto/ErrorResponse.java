package com.historiamed.backend.common.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Formato único de error para toda la API.
 *
 * @param errores errores de validación por campo (vacío si no aplica)
 */
public record ErrorResponse(Instant fecha, int status, String error, String mensaje, String ruta,
		Map<String, String> errores) {

	public static ErrorResponse de(int status, String error, String mensaje, String ruta) {
		return new ErrorResponse(Instant.now(), status, error, mensaje, ruta, Map.of());
	}

}
