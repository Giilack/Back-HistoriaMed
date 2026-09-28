package com.historiamed.backend.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GeneradorPasswordTest {

	private final GeneradorPassword generador = new GeneradorPassword();

	@Test
	void cumpleLaPoliticaDeContrasenas() {
		for (int i = 0; i < 500; i++) {
			String password = generador.generar();
			assertThat(password).hasSize(12).matches("^(?=.*[A-Za-z])(?=.*\\d).{8,72}$");
		}
	}

	@Test
	void noRepiteContrasenas() {
		Set<String> generadas = new HashSet<>();
		for (int i = 0; i < 500; i++) {
			generadas.add(generador.generar());
		}
		assertThat(generadas).hasSize(500);
	}

}
