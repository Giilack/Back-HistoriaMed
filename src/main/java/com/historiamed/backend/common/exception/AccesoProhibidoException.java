package com.historiamed.backend.common.exception;

/**
 * El usuario tiene el rol adecuado pero no puede operar sobre este recurso concreto (por ejemplo, la atención de
 * otro médico). Se responde con 403 y el mensaje explicativo.
 */
public class AccesoProhibidoException extends RuntimeException {

	public AccesoProhibidoException(String mensaje) {
		super(mensaje);
	}

}
