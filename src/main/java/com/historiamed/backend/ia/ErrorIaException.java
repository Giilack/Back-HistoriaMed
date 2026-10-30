package com.historiamed.backend.ia;

/**
 * El servicio de IA no pudo responder (documento o pregunta). El mensaje se muestra al usuario, que puede reintentar
 * o seguir sin la IA.
 */
public class ErrorIaException extends RuntimeException {

	public ErrorIaException(String mensaje) {
		super(mensaje);
	}

	public ErrorIaException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}

}
