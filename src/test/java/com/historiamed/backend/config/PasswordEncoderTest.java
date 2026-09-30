package com.historiamed.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderTest {

	private final PasswordEncoder codificador = SecurityConfig.crearPasswordEncoder();

	@Test
	void lasContrasenasNuevasUsanArgon2id() {
		String hash = codificador.encode("Clave2026");

		assertThat(hash).startsWith("{argon2}$argon2id$");
		assertThat(hash.length()).isLessThanOrEqualTo(255);
		assertThat(codificador.matches("Clave2026", hash)).isTrue();
		assertThat(codificador.matches("otra", hash)).isFalse();
		assertThat(codificador.upgradeEncoding(hash)).isFalse();
	}

	@Test
	void losHashesBcryptAnterioresSiguenValiendoYSeMarcanParaActualizar() {
		// Así quedaron guardados antes de Argon2id: BCrypt sin prefijo
		String antiguo = new BCryptPasswordEncoder().encode("Clave2026");

		assertThat(codificador.matches("Clave2026", antiguo)).isTrue();
		assertThat(codificador.matches("otra", antiguo)).isFalse();
		assertThat(codificador.upgradeEncoding(antiguo)).isTrue();
	}

}
