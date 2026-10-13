package com.historiamed.backend.demo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.common.security.CifradoDatos;
import com.historiamed.backend.triaje.EvaluadorTriaje;

class CargaDatosDemoTest {

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "corta1", "sinnumeros", "12345678" })
	void sinDemoPasswordValidaNoCargaNada(String password) {
		CargaDatosDemo carga = new CargaDatosDemo(jdbc, passwordEncoder, mock(EvaluadorTriaje.class),
				mock(AuditoriaService.class), mock(CifradoDatos.class), password);

		carga.run(null);

		verifyNoInteractions(jdbc, passwordEncoder);
	}

}
