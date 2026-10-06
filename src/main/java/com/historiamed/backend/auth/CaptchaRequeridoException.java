package com.historiamed.backend.auth;

/**
 * Login rechazado porque, tras varios intentos fallidos con el mismo usuario, hay que resolver el CAPTCHA. Se responde
 * con 401 y el código CAPTCHA_REQUERIDO, para que el frontend muestre el CAPTCHA.
 */
public class CaptchaRequeridoException extends CredencialesInvalidasException {

	public CaptchaRequeridoException(String mensaje) {
		super(mensaje);
	}

}
