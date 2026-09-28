package com.historiamed.backend.triaje;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.historiamed.backend.cita.Prioridad;
import com.historiamed.backend.triaje.EvaluadorTriaje.Condiciones;
import com.historiamed.backend.triaje.EvaluadorTriaje.Evaluacion;
import com.historiamed.backend.triaje.EvaluadorTriaje.Signos;

class EvaluadorTriajeTest {

	private final EvaluadorTriaje evaluador = new EvaluadorTriaje();

	private static final Condiciones ADULTO = new Condiciones(35, false, false);

	/** Signos normales de un adulto; cada prueba cambia solo lo que le interesa. */
	private static Signos normales() {
		return new Signos(120, 80, 75, 16, new BigDecimal("36.8"), 98, new BigDecimal("70"), new BigDecimal("170"));
	}

	private static Signos con(Integer pas, Integer pad, int fc, Integer fr, String temp, int sat) {
		return new Signos(pas, pad, fc, fr, new BigDecimal(temp), sat, new BigDecimal("70"), null);
	}

	private static List<String> codigos(Evaluacion e) {
		return e.alertas().stream().map(Alerta::getCodigo).toList();
	}

	@Test
	void adultoConSignosNormalesNoTieneAlertasYEsNormal() {
		Evaluacion e = evaluador.evaluar(normales(), ADULTO);

		assertThat(e.alertas()).isEmpty();
		assertThat(e.prioridadSugerida()).isEqualTo(Prioridad.NORMAL);
		assertThat(e.imc()).isEqualByComparingTo("24.2");
	}

	@Test
	void saturacionBajaEsAdvertenciaYMuyBajaEsUrgente() {
		assertThat(codigos(evaluador.evaluar(con(120, 80, 75, 16, "36.8", 91), ADULTO)))
			.containsExactly("SATURACION_BAJA");
		Evaluacion critica = evaluador.evaluar(con(120, 80, 75, 16, "36.8", 88), ADULTO);
		assertThat(codigos(critica)).containsExactly("SATURACION_MUY_BAJA");
		assertThat(critica.prioridadSugerida()).isEqualTo(Prioridad.URGENTE);
		// 92 % ya no alerta
		assertThat(evaluador.evaluar(con(120, 80, 75, 16, "36.8", 92), ADULTO).alertas()).isEmpty();
	}

	@Test
	void temperatura() {
		assertThat(codigos(evaluador.evaluar(con(120, 80, 75, 16, "38.0", 98), ADULTO))).containsExactly("FIEBRE");
		assertThat(codigos(evaluador.evaluar(con(120, 80, 75, 16, "40.1", 98), ADULTO)))
			.containsExactly("HIPERPIREXIA");
		assertThat(codigos(evaluador.evaluar(con(120, 80, 75, 16, "34.9", 98), ADULTO)))
			.containsExactly("HIPOTERMIA");
		assertThat(evaluador.evaluar(con(120, 80, 75, 16, "37.9", 98), ADULTO).alertas()).isEmpty();
	}

	@Test
	void presionArterial() {
		assertThat(codigos(evaluador.evaluar(con(185, 95, 75, 16, "36.8", 98), ADULTO)))
			.containsExactly("CRISIS_HIPERTENSIVA");
		assertThat(codigos(evaluador.evaluar(con(150, 112, 75, 16, "36.8", 98), ADULTO)))
			.containsExactly("CRISIS_HIPERTENSIVA");
		assertThat(codigos(evaluador.evaluar(con(142, 85, 75, 16, "36.8", 98), ADULTO)))
			.containsExactly("PRESION_ELEVADA");
		assertThat(codigos(evaluador.evaluar(con(85, 55, 75, 16, "36.8", 98), ADULTO))).containsExactly("HIPOTENSION");
		assertThat(codigos(evaluador.evaluar(con(75, 45, 75, 16, "36.8", 98), ADULTO)))
			.containsExactly("HIPOTENSION_SEVERA");
		// Sin presión registrada no hay alerta de presión
		assertThat(evaluador.evaluar(con(null, null, 75, 16, "36.8", 98), ADULTO).alertas()).isEmpty();
	}

	@Test
	void frecuenciasCardiacaYRespiratoria() {
		assertThat(codigos(evaluador.evaluar(con(120, 80, 110, 16, "36.8", 98), ADULTO)))
			.containsExactly("TAQUICARDIA");
		assertThat(codigos(evaluador.evaluar(con(120, 80, 45, 16, "36.8", 98), ADULTO)))
			.containsExactly("BRADICARDIA");
		assertThat(codigos(evaluador.evaluar(con(120, 80, 140, 16, "36.8", 98), ADULTO)))
			.containsExactly("FC_CRITICA");
		assertThat(codigos(evaluador.evaluar(con(120, 80, 75, 28, "36.8", 98), ADULTO))).containsExactly("TAQUIPNEA");
		assertThat(codigos(evaluador.evaluar(con(120, 80, 75, 34, "36.8", 98), ADULTO)))
			.containsExactly("FR_CRITICA");
	}

	@Test
	void enNiniosNoSeAplicanUmbralesDeAdultoDeFrecuenciaNiPresion() {
		// FC 130 y FR 30 son normales en un lactante
		Condiciones lactante = new Condiciones(0, false, false);
		Evaluacion e = evaluador.evaluar(con(null, null, 130, 30, "36.8", 98), lactante);

		assertThat(e.alertas()).isEmpty();
		// Pero la fiebre y la saturación sí se evalúan
		assertThat(codigos(evaluador.evaluar(con(null, null, 130, 30, "39.0", 89), lactante)))
			.containsExactlyInAnyOrder("FIEBRE", "SATURACION_MUY_BAJA");
	}

	@Test
	void prioridadPreferente() {
		assertThat(evaluador.evaluar(normales(), new Condiciones(65, false, false)).prioridadSugerida())
			.as("adulto mayor")
			.isEqualTo(Prioridad.PREFERENTE);
		assertThat(evaluador.evaluar(normales(), new Condiciones(8, false, false)).prioridadSugerida()).as("niño")
			.isEqualTo(Prioridad.PREFERENTE);
		assertThat(evaluador.evaluar(normales(), new Condiciones(28, true, false)).prioridadSugerida())
			.as("gestante")
			.isEqualTo(Prioridad.PREFERENTE);
		assertThat(evaluador.evaluar(normales(), new Condiciones(40, false, true)).prioridadSugerida())
			.as("discapacidad")
			.isEqualTo(Prioridad.PREFERENTE);
	}

	@Test
	void unaAlertaCriticaGanaALaCondicionPreferente() {
		Evaluacion e = evaluador.evaluar(con(120, 80, 75, 16, "36.8", 85), new Condiciones(70, false, false));

		assertThat(e.prioridadSugerida()).isEqualTo(Prioridad.URGENTE);
	}

	@Test
	void imc() {
		assertThat(EvaluadorTriaje.imc(new BigDecimal("95"), new BigDecimal("170"))).isEqualByComparingTo("32.9");
		assertThat(EvaluadorTriaje.imc(new BigDecimal("70"), null)).isNull();
		Evaluacion obeso = evaluador.evaluar(new Signos(120, 80, 75, 16, new BigDecimal("36.8"), 98,
				new BigDecimal("95"), new BigDecimal("170")), ADULTO);
		assertThat(codigos(obeso)).containsExactly("OBESIDAD");
		// El IMC no alerta en menores de 18 (se interpreta con percentiles)
		assertThat(evaluador.evaluar(new Signos(null, null, 90, 20, new BigDecimal("36.8"), 98, new BigDecimal("60"),
				new BigDecimal("150")), new Condiciones(15, false, false))
			.alertas()).isEmpty();
	}

}
