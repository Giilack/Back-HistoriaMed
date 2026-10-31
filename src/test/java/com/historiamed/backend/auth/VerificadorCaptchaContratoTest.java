package com.historiamed.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Interoperabilidad con Cloudflare Turnstile: un servidor local responde como siteverify. */
class VerificadorCaptchaContratoTest {

	private HttpServer servidor;

	private final AtomicReference<String> formulario = new AtomicReference<>();

	private String respuesta = "{\"success\": true}";

	private VerificadorCaptcha verificador;

	@BeforeEach
	void levantarServidor() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		servidor.createContext("/siteverify", this::responder);
		servidor.start();
		verificador = new VerificadorCaptcha(new CaptchaProperties("secreto-ficticio", 3,
				"http://localhost:" + servidor.getAddress().getPort() + "/siteverify"));
	}

	@AfterEach
	void apagarServidor() {
		servidor.stop(0);
	}

	private void responder(HttpExchange intercambio) throws IOException {
		formulario.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
		byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
		intercambio.getResponseHeaders().add("Content-Type", "application/json");
		intercambio.sendResponseHeaders(200, bytes.length);
		intercambio.getResponseBody().write(bytes);
		intercambio.close();
	}

	@Test
	@DisplayName("INT-T1 Envía el secreto y el token, y acepta si Turnstile aprueba")
	void tokenAprobado() {
		assertThat(verificador.verificar("token-de-prueba")).isTrue();
		assertThat(formulario.get()).contains("secret=secreto-ficticio").contains("response=token-de-prueba");
	}

	@Test
	@DisplayName("INT-T2 Rechaza si Turnstile no aprueba")
	void tokenRechazado() {
		respuesta = "{\"success\": false, \"error-codes\": [\"invalid-input-response\"]}";

		assertThat(verificador.verificar("token-de-prueba")).isFalse();
	}

	@Test
	@DisplayName("INT-T3 Sin servicio se rechaza: no se abre la puerta a un ataque")
	void servicioCaido() {
		servidor.stop(0);

		assertThat(verificador.verificar("token-de-prueba")).isFalse();
	}

	@Test
	@DisplayName("INT-T4 Un token vacío o enorme ni siquiera se envía")
	void tokenInvalido() {
		assertThat(verificador.verificar(" ")).isFalse();
		assertThat(verificador.verificar("x".repeat(3000))).isFalse();
		assertThat(formulario.get()).isNull();
	}

}
