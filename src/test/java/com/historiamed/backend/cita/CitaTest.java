package com.historiamed.backend.cita;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.historiamed.backend.common.exception.ReglaNegocioException;

class CitaTest {

	private final Instant ahora = Instant.parse("2026-09-28T14:00:00Z");

	@Test
	void flujoCompletoGuardaLaHoraDeCadaPaso() {
		Cita c = new Cita();
		c.iniciar(false, ahora);
		c.cambiarEstado(EstadoCita.EN_ESPERA_TRIAJE, ahora.plusSeconds(60));
		c.cambiarEstado(EstadoCita.EN_ESPERA_CONSULTA, ahora.plusSeconds(600));
		c.cambiarEstado(EstadoCita.EN_CONSULTA, ahora.plusSeconds(1200));
		c.cambiarEstado(EstadoCita.ATENDIDO, ahora.plusSeconds(2400));

		assertThat(c.getEstado()).isEqualTo(EstadoCita.ATENDIDO);
		assertThat(c.getLlegadaEn()).isEqualTo(ahora.plusSeconds(60));
		assertThat(c.getTriajeEn()).isEqualTo(ahora.plusSeconds(600));
		assertThat(c.getConsultaInicioEn()).isEqualTo(ahora.plusSeconds(1200));
		assertThat(c.getAtendidoEn()).isEqualTo(ahora.plusSeconds(2400));
	}

	@Test
	void noSePuedeSaltarElTriaje() {
		Cita c = new Cita();
		c.iniciar(false, ahora);
		c.cambiarEstado(EstadoCita.EN_ESPERA_TRIAJE, ahora);

		assertThatThrownBy(() -> c.cambiarEstado(EstadoCita.EN_CONSULTA, ahora))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("EN_ESPERA_TRIAJE a EN_CONSULTA");
	}

	@Test
	void llegadaSinCitaEntraDirectoALaColaDeTriaje() {
		Cita c = new Cita();
		c.iniciar(true, ahora);

		assertThat(c.getEstado()).isEqualTo(EstadoCita.EN_ESPERA_TRIAJE);
		assertThat(c.isSinCita()).isTrue();
		assertThat(c.getLlegadaEn()).isEqualTo(ahora);
	}

	@Test
	void unaCitaEnColaNoSeCancelaYUnaTerminadaNoCambia() {
		Cita c = new Cita();
		c.iniciar(true, ahora);
		assertThatThrownBy(() -> c.cambiarEstado(EstadoCita.CANCELADA, ahora))
			.isInstanceOf(ReglaNegocioException.class);

		c.cambiarEstado(EstadoCita.NO_SE_PRESENTO, ahora);
		for (EstadoCita e : EstadoCita.values()) {
			assertThat(c.getEstado().puedePasarA(e)).isFalse();
		}
		assertThat(c.getEstado().esActivo()).isFalse();
	}

}
