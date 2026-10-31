package com.historiamed.backend.ia;

/** La capa gratuita rechazó la consulta por exceso de uso: conviene esperar y repetirla. */
public class LimiteIaException extends ErrorIaException {

	public LimiteIaException(String mensaje) {
		super(mensaje);
	}

}
