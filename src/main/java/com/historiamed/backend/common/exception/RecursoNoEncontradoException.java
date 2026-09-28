package com.historiamed.backend.common.exception;

/**
 * El recurso solicitado no existe. Se responde con 404.
 */
public class RecursoNoEncontradoException extends RuntimeException {

	public RecursoNoEncontradoException(String recurso, Object id) {
		super(recurso + " no encontrado: " + id);
	}

}
