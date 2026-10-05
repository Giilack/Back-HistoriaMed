package com.historiamed.backend.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class ProveedorGeminiTest {

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void tomaElTextoGeneradoDeLaRespuesta() {
		var respuesta = json.readTree("""
				{"candidates": [{"content": {"parts": [{"text": "{\\"datos\\": []}"}]}, "finishReason": "STOP"}]}""");

		assertThat(ProveedorGemini.textoDeRespuesta(respuesta)).isEqualTo("{\"datos\": []}");
	}

	@Test
	void explicaCuandoLaIaBloqueaElDocumento() {
		var respuesta = json.readTree("""
				{"promptFeedback": {"blockReason": "SAFETY"}}""");

		assertThatThrownBy(() -> ProveedorGemini.textoDeRespuesta(respuesta)).isInstanceOf(ErrorIaException.class)
			.hasMessageContaining("SAFETY");
	}

	@Test
	void explicaCuandoNoHayTexto() {
		var respuesta = json.readTree("""
				{"candidates": [{"finishReason": "MAX_TOKENS"}]}""");

		assertThatThrownBy(() -> ProveedorGemini.textoDeRespuesta(respuesta)).hasMessageContaining("MAX_TOKENS");
	}

	@Test
	void sinClaveLaIaQuedaDesactivada() {
		var proveedor = new ProveedorGemini(" ", "gemini-prueba", 30, json);

		assertThat(proveedor.disponible()).isFalse();
		assertThatThrownBy(() -> proveedor.extraerJson(new byte[0], "application/pdf", "", java.util.Map.of()))
			.isInstanceOf(ErrorIaException.class);
	}

}
