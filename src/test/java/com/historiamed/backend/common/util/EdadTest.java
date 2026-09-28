package com.historiamed.backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class EdadTest {

	private static final LocalDate HOY = LocalDate.of(2026, 9, 27);

	@Test
	void adultoEnAnios() {
		assertThat(Edad.texto(LocalDate.of(1990, 9, 28), HOY)).isEqualTo("35 años");
		assertThat(Edad.texto(LocalDate.of(1990, 9, 27), HOY)).isEqualTo("36 años");
		assertThat(Edad.texto(LocalDate.of(2025, 9, 27), HOY)).isEqualTo("1 año");
	}

	@Test
	void lactanteEnMeses() {
		assertThat(Edad.texto(LocalDate.of(2026, 1, 10), HOY)).isEqualTo("8 meses");
		assertThat(Edad.texto(LocalDate.of(2026, 8, 20), HOY)).isEqualTo("1 mes");
	}

	@Test
	void recienNacidoEnDias() {
		assertThat(Edad.texto(LocalDate.of(2026, 9, 15), HOY)).isEqualTo("12 días");
		assertThat(Edad.texto(HOY, HOY)).isEqualTo("0 días");
	}

}
