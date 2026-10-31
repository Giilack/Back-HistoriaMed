package com.historiamed.backend.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Interoperabilidad con la API de Gemini: un servidor HTTP local responde lo que respondería Google, para verificar
 * el contrato (ruta, cabeceras, cuerpo) y la reacción a cada error sin gastar la cuota gratuita.
 */
class ProveedorGeminiContratoTest {

	private static final Map<String, Object> ESQUEMA = Map.of("type", "OBJECT");

	private final JsonMapper json = JsonMapper.builder().build();

	private HttpServer servidor;

	private final AtomicReference<HttpExchange> ultima = new AtomicReference<>();

	private final AtomicReference<String> cuerpoRecibido = new AtomicReference<>();

	private int estado = 200;

	private String respuesta = """
			{"candidates": [{"content": {"parts": [{"text": "{\\"ok\\": true}"}]}, "finishReason": "STOP"}]}""";

	private ProveedorGemini proveedor;

	@BeforeEach
	void levantarServidor() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		servidor.createContext("/", this::responder);
		servidor.start();
		String url = "http://localhost:" + servidor.getAddress().getPort() + "/v1beta/models/%s:generateContent";
		proveedor = new ProveedorGemini("clave-ficticia", "modelo-prueba", 5, json, url);
	}

	@AfterEach
	void apagarServidor() {
		servidor.stop(0);
	}

	private void responder(HttpExchange intercambio) throws IOException {
		ultima.set(intercambio);
		cuerpoRecibido.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
		byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
		intercambio.sendResponseHeaders(estado, bytes.length);
		intercambio.getResponseBody().write(bytes);
		intercambio.close();
	}

	@Test
	@DisplayName("INT-G1 Envía el pedido en el formato de generateContent y lee la respuesta")
	void respetaElContrato() {
		String resultado = proveedor.responderJson("instrucciones", "contenido ficticio", ESQUEMA);

		assertThat(resultado).isEqualTo("{\"ok\": true}");
		assertThat(ultima.get().getRequestURI().getPath()).isEqualTo("/v1beta/models/modelo-prueba:generateContent");
		JsonNode cuerpo = json.readTree(cuerpoRecibido.get());
		assertThat(cuerpo.path("contents").get(0).path("parts")).hasSize(2);
		assertThat(cuerpo.path("generationConfig").path("responseMimeType").asString()).isEqualTo("application/json");
		assertThat(cuerpo.path("generationConfig").path("responseSchema").path("type").asString()).isEqualTo("OBJECT");
	}

	@Test
	@DisplayName("INT-G2 La clave viaja en una cabecera, nunca en la URL")
	void laClaveNoVaEnLaUrl() {
		proveedor.responderJson("instrucciones", "contenido ficticio", ESQUEMA);

		assertThat(ultima.get().getRequestHeaders().getFirst("x-goog-api-key")).isEqualTo("clave-ficticia");
		assertThat(ultima.get().getRequestURI().toString()).doesNotContain("clave-ficticia");
	}

	@Test
	@DisplayName("INT-G3 Envía el documento en Base64 con su tipo")
	void enviaElDocumento() {
		byte[] pdf = "%PDF-1.4 ficticio".getBytes(StandardCharsets.US_ASCII);

		proveedor.extraerJson(pdf, "application/pdf", "extrae los datos", ESQUEMA);

		JsonNode partes = json.readTree(cuerpoRecibido.get()).path("contents").get(0).path("parts");
		assertThat(partes.get(0).path("inlineData").path("mimeType").asString()).isEqualTo("application/pdf");
		assertThat(partes.get(0).path("inlineData").path("data").asString()).isNotBlank();
	}

	@Test
	@DisplayName("INT-G4 Cada error del servicio se traduce a un mensaje claro")
	void traduceLosErrores() {
		for (var caso : List.of(Map.entry(429, "límite de uso gratuito"), Map.entry(401, "GEMINI_API_KEY"),
				Map.entry(404, "no existe"), Map.entry(503, "no está disponible"))) {
			estado = caso.getKey();
			respuesta = "{\"error\": {\"message\": \"detalle\"}}";

			assertThatThrownBy(() -> proveedor.responderJson("i", "c", ESQUEMA)).isInstanceOf(ErrorIaException.class)
				.hasMessageContaining(caso.getValue());
		}
	}

	@Test
	@DisplayName("INT-G5 Una respuesta bloqueada o vacía no se toma como válida")
	void respuestaSinTexto() {
		respuesta = "{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}";

		assertThatThrownBy(() -> proveedor.responderJson("i", "c", ESQUEMA)).isInstanceOf(ErrorIaException.class);
	}

	@Test
	@DisplayName("INT-G6 Si el servicio no está, el error se controla y no tumba el sistema")
	void servicioCaido() {
		servidor.stop(0);

		assertThatThrownBy(() -> proveedor.responderJson("i", "c", ESQUEMA)).isInstanceOf(ErrorIaException.class);
	}

}
