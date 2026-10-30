package com.historiamed.backend.ia;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * IA de Google (API de Gemini, método {@code generateContent}). El documento se envía adjunto y la respuesta se
 * pide en JSON con un esquema fijo.
 * <p>
 * <b>Solo con datos ficticios.</b> En la capa gratuita, las condiciones de Google indican que puede usar lo enviado
 * para mejorar sus productos y que puede haber revisión humana, y piden no enviar información personal o
 * sensible. Con pacientes reales habría que usar la capa de pago (que no usa los datos para entrenar) o un modelo
 * local (plan.md, principio P6).
 * <p>
 * Configuración: {@code GEMINI_API_KEY} (sin ella, la IA queda desactivada) y {@code GEMINI_MODELO}.
 */
@Component
public class ProveedorGemini implements ProveedorIA {

	private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

	private final String clave;

	private final String modelo;

	private final Duration tiempoMaximo;

	private final JsonMapper json;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	public ProveedorGemini(@Value("${historiamed.ia.gemini.api-key:}") String clave,
			@Value("${historiamed.ia.gemini.modelo:gemini-3.5-flash-lite}") String modelo,
			@Value("${historiamed.ia.gemini.segundos-maximos:120}") long segundosMaximos, JsonMapper json) {
		this.clave = clave == null ? "" : clave.trim();
		this.modelo = modelo.trim();
		this.tiempoMaximo = Duration.ofSeconds(segundosMaximos);
		this.json = json;
	}

	@Override
	public boolean disponible() {
		return !clave.isEmpty();
	}

	@Override
	public String modelo() {
		return modelo;
	}

	@Override
	public String extraerJson(byte[] documento, String mimeType, String instrucciones, Map<String, Object> esquema) {
		return generar(List.of(
				Map.of("inlineData", Map.of("mimeType", mimeType, "data", Base64.getEncoder().encodeToString(documento))),
				Map.of("text", instrucciones)), esquema);
	}

	@Override
	public String responderJson(String instrucciones, String contenido, Map<String, Object> esquema) {
		return generar(List.of(Map.of("text", instrucciones), Map.of("text", contenido)), esquema);
	}

	/** Llama a generateContent con las partes indicadas y devuelve el JSON generado. */
	private String generar(List<Map<String, Object>> partes, Map<String, Object> esquema) {
		if (!disponible()) {
			throw new ErrorIaException("La IA no está configurada (falta GEMINI_API_KEY)");
		}
		Map<String, Object> cuerpo = Map.of(
				"contents", List.of(Map.of("parts", partes)),
				"generationConfig", Map.of(
						"responseMimeType", "application/json",
						"responseSchema", esquema,
						// Sin creatividad: se busca responder con los datos, no inventar
						"temperature", 0));

		HttpRequest peticion = HttpRequest.newBuilder(URI.create(URL.formatted(modelo)))
			.timeout(tiempoMaximo)
			.header("Content-Type", "application/json")
			// La clave va en una cabecera, no en la URL: así no queda en registros de acceso
			.header("x-goog-api-key", clave)
			.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
			.build();

		HttpResponse<String> respuesta;
		try {
			respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
		}
		catch (HttpTimeoutException e) {
			throw new ErrorIaException("La IA tardó demasiado en responder. Intente de nuevo o llene los datos a mano", e);
		}
		catch (IOException e) {
			throw new ErrorIaException("No se pudo conectar con el servicio de IA", e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ErrorIaException("Se interrumpió la consulta a la IA", e);
		}
		if (respuesta.statusCode() != 200) {
			throw new ErrorIaException(mensajeDeError(respuesta.statusCode(), respuesta.body()));
		}
		return textoDeRespuesta(json.readTree(respuesta.body()));
	}

	/** Extrae el texto generado de la respuesta de generateContent, o explica por qué no lo hay. */
	static String textoDeRespuesta(JsonNode respuesta) {
		JsonNode candidato = respuesta.path("candidates").path(0);
		String texto = candidato.path("content").path("parts").path(0).path("text").asString("");
		if (!texto.isBlank()) {
			return texto;
		}
		String bloqueo = respuesta.path("promptFeedback").path("blockReason").asString("");
		if (!bloqueo.isEmpty()) {
			throw new ErrorIaException("La IA rechazó el contenido (" + bloqueo + ")");
		}
		String fin = candidato.path("finishReason").asString("");
		throw new ErrorIaException("La IA no devolvió datos" + (fin.isEmpty() ? "" : " (" + fin + ")"));
	}

	private String mensajeDeError(int estado, String cuerpo) {
		String detalle = "";
		try {
			detalle = json.readTree(cuerpo).path("error").path("message").asString("");
		}
		catch (RuntimeException ignorada) {
			// El cuerpo no era JSON: basta con el código de estado
		}
		return switch (estado) {
			case 400 -> "La IA rechazó la solicitud" + (detalle.isEmpty() ? "" : ": " + detalle);
			case 401, 403 -> "La clave de la IA (GEMINI_API_KEY) no es válida o no tiene permiso para el modelo "
					+ modelo;
			case 404 -> "El modelo " + modelo + " no existe o ya no está disponible (revise GEMINI_MODELO)";
			case 429 -> "Se alcanzó el límite de uso gratuito de la IA. Intente en unos minutos o llene los datos a mano";
			default -> estado >= 500 ? "El servicio de IA no está disponible en este momento (error " + estado + ")"
					: "Error " + estado + " del servicio de IA" + (detalle.isEmpty() ? "" : ": " + detalle);
		};
	}

}
