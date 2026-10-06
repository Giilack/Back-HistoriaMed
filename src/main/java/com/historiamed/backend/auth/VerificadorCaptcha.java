package com.historiamed.backend.auth;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

/**
 * Comprueba con Cloudflare Turnstile que el token del CAPTCHA lo resolvió una persona. El token es de un solo uso y
 * la clave secreta nunca sale del backend.
 * <p>
 * Si el servicio no responde, se considera no resuelto: es preferible que alguien con varios intentos fallidos
 * espere a que vuelva el servicio a dejar pasar un ataque automatizado.
 */
@Component
public class VerificadorCaptcha {

	private static final Logger log = LoggerFactory.getLogger(VerificadorCaptcha.class);

	private final CaptchaProperties properties;

	private final RestClient cliente;

	public VerificadorCaptcha(CaptchaProperties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(Duration.ofSeconds(5));
		fabrica.setReadTimeout(Duration.ofSeconds(5));
		this.cliente = RestClient.builder().requestFactory(fabrica).build();
	}

	public boolean activo() {
		return properties.activo();
	}

	/** Intentos fallidos con el mismo usuario a partir de los cuales se exige el CAPTCHA. */
	public int intentosSinCaptcha() {
		return properties.intentosSinCaptcha();
	}

	public boolean verificar(String token) {
		if (token == null || token.isBlank() || token.length() > 2048) {
			return false;
		}
		MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
		formulario.add("secret", properties.secreto());
		formulario.add("response", token);
		try {
			JsonNode respuesta = cliente.post()
				.uri(properties.urlVerificacion())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(formulario)
				.retrieve()
				.body(JsonNode.class);
			return respuesta != null && respuesta.path("success").asBoolean(false);
		}
		catch (RestClientException e) {
			log.warn("No se pudo verificar el CAPTCHA: {}", e.getMessage());
			return false;
		}
	}

}
