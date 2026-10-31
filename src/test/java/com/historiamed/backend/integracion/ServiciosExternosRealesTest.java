package com.historiamed.backend.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.historiamed.backend.auth.CaptchaProperties;
import com.historiamed.backend.auth.VerificadorCaptcha;
import com.historiamed.backend.ia.ProveedorGemini;

import tools.jackson.databind.json.JsonMapper;

/**
 * Prueba contra los servicios reales. Solo corre con PRUEBAS_EXTERNAS=true: en el CI no se gasta la cuota de la IA y
 * una caída de Google o Cloudflare no debe impedir unir un Pull Request. Solo envía datos ficticios.
 */
@EnabledIfEnvironmentVariable(named = "PRUEBAS_EXTERNAS", matches = "true")
class ServiciosExternosRealesTest {

	private static final String URL_TURNSTILE = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

	/** Claves públicas de prueba que Cloudflare documenta: una siempre aprueba y la otra siempre rechaza. */
	private static final String SECRETO_APRUEBA = "1x0000000000000000000000000000000AA";

	private static final String SECRETO_RECHAZA = "2x0000000000000000000000000000000AA";

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	@DisplayName("INT-R1 Gemini real responde JSON según el esquema")
	@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
	void geminiReal() {
		String modelo = System.getenv().getOrDefault("GEMINI_MODELO", "gemini-3.5-flash-lite");
		var proveedor = new ProveedorGemini(System.getenv("GEMINI_API_KEY"), modelo, 60, json);
		Map<String, Object> esquema = Map.of("type", "OBJECT", "properties", Map.of("color", Map.of("type", "STRING")),
				"required", java.util.List.of("color"));

		String resultado = proveedor.responderJson("Responde el color que se menciona.", "El auto ficticio es rojo.", esquema);

		assertThat(json.readTree(resultado).path("color").asString().toLowerCase()).contains("rojo");
	}

	@Test
	@DisplayName("INT-R2 Cloudflare Turnstile real aprueba con la clave de prueba")
	void turnstileAprueba() {
		var verificador = new VerificadorCaptcha(new CaptchaProperties(SECRETO_APRUEBA, 3, URL_TURNSTILE));

		assertThat(verificador.verificar("XXXX.DUMMY.TOKEN.XXXX")).isTrue();
	}

	@Test
	@DisplayName("INT-R3 Cloudflare Turnstile real rechaza con la clave de prueba")
	void turnstileRechaza() {
		var verificador = new VerificadorCaptcha(new CaptchaProperties(SECRETO_RECHAZA, 3, URL_TURNSTILE));

		assertThat(verificador.verificar("XXXX.DUMMY.TOKEN.XXXX")).isFalse();
	}

}
