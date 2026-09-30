package com.historiamed.backend.atencion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.usuario.Usuario;

class AtencionTest {

	@Test
	void unaAtencionCerradaNoSeModificaSoloAdmiteAdendas() {
		Atencion a = new Atencion();
		a.cerrar(Instant.now());

		assertThat(a.estaCerrada()).isTrue();
		assertThat(a.getCerradaEn()).isNotNull();
		assertThatThrownBy(() -> a.reemplazarContenido(List.of(), List.of(), List.of()))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("adenda");
		assertThatThrownBy(() -> a.cerrar(Instant.now())).isInstanceOf(ReglaNegocioException.class);

		Adenda adenda = a.agregarAdenda(new Usuario(), "Se corrige la dosis indicada: 1 tableta cada 12 horas");
		assertThat(a.getAdendas()).containsExactly(adenda);
	}

	@Test
	void unaAtencionEnCursoNoLlevaAdendas() {
		Atencion a = new Atencion();

		assertThatThrownBy(() -> a.agregarAdenda(new Usuario(), "texto"))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("en curso");
	}

}
