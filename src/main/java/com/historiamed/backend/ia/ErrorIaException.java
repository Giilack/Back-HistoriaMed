package com.historiamed.backend.ia;

/**
 * El servicio de IA no pudo procesar el documento. El mensaje se muestra al usuario, que puede reintentar o llenar
 * los datos a mano.
 */
public class ErrorIaException extends RuntimeException {

	public ErrorIaException(String mensaje) {
		super(mensaje);
	}

	public ErrorIaException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}

}
