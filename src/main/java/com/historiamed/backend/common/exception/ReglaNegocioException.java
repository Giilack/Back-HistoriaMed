package com.historiamed.backend.common.exception;

/**
 * La operación viola una regla de negocio (por ejemplo, un DNI duplicado). Se responde con 409.
 */
public class ReglaNegocioException extends RuntimeException {

	public ReglaNegocioException(String mensaje) {
		super(mensaje);
	}

}
