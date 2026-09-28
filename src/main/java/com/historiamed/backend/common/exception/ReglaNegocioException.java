package com.historiamed.backend.common.exception;

import java.util.Map;

import lombok.Getter;

/**
 * La operación viola una regla de negocio (por ejemplo, un DNI duplicado). Se responde con 409.
 *
 * <p>
 * Opcionalmente lleva un código (para que el cliente reconozca el caso, por ejemplo {@code ALERGIA_MEDICAMENTO})
 * y detalles por campo.
 */
@Getter
public class ReglaNegocioException extends RuntimeException {

	private final String codigo;

	private final Map<String, String> detalles;

	public ReglaNegocioException(String mensaje) {
		this("REGLA_DE_NEGOCIO", mensaje, Map.of());
	}

	public ReglaNegocioException(String codigo, String mensaje, Map<String, String> detalles) {
		super(mensaje);
		this.codigo = codigo;
		this.detalles = detalles;
	}

}
