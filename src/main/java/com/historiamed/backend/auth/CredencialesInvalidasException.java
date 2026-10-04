package com.historiamed.backend.auth;

/**
 * Login o sesión inválidos. Se responde con 401.
 */
public class CredencialesInvalidasException extends RuntimeException {

	public CredencialesInvalidasException() {
		super("Credenciales inválidas");
	}

	public CredencialesInvalidasException(String mensaje) {
		super(mensaje);
	}

}
