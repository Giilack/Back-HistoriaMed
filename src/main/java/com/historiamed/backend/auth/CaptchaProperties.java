package com.historiamed.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CAPTCHA del inicio de sesión (Cloudflare Turnstile). Sin {@code secreto} el CAPTCHA queda desactivado y el login
 * funciona como antes (útil en local y en las pruebas).
 *
 * @param secreto clave secreta de Turnstile (variable TURNSTILE_SECRET); nunca va al frontend
 * @param intentosSinCaptcha intentos fallidos con el mismo usuario antes de exigir el CAPTCHA
 * @param urlVerificacion servicio que valida el token resuelto por el navegador
 */
@ConfigurationProperties("historiamed.captcha")
public record CaptchaProperties(String secreto, int intentosSinCaptcha, String urlVerificacion) {

	public boolean activo() {
		return secreto != null && !secreto.isBlank();
	}

}
