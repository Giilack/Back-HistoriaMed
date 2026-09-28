package com.historiamed.backend.triaje;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.historiamed.backend.cita.Prioridad;

/**
 * Reglas fijas de triaje (plan.md, sección 5.3 y principio P8: las reglas críticas no dependen de la IA).
 *
 * <p>
 * Los umbrales de presión, frecuencia cardiaca y frecuencia respiratoria son de adultos y adolescentes: en menores
 * de 12 años esos valores normales dependen de la edad, así que solo se evalúan temperatura y saturación. Es una
 * limitación conocida del sistema.
 */
@Component
public class EvaluadorTriaje {

	/** Desde esta edad se aplican los umbrales de adulto (y la condición de "niño" para la prioridad). */
	static final int EDAD_UMBRALES_ADULTO = 12;

	/** Persona adulta mayor en el Perú (Ley 30490). */
	static final int EDAD_ADULTO_MAYOR = 60;

	/** Datos de entrada ya validados en rango. Los opcionales pueden ser null. */
	public record Signos(Integer presionSistolica, Integer presionDiastolica, int frecuenciaCardiaca,
			Integer frecuenciaRespiratoria, BigDecimal temperatura, int saturacion, BigDecimal peso,
			BigDecimal talla) {
	}

	public record Condiciones(int edadAnios, boolean gestante, boolean discapacidad) {
	}

	public record Evaluacion(List<Alerta> alertas, BigDecimal imc, Prioridad prioridadSugerida) {
	}

	public Evaluacion evaluar(Signos s, Condiciones c) {
		List<Alerta> alertas = new ArrayList<>();
		evaluarSaturacion(s.saturacion(), alertas);
		evaluarTemperatura(s.temperatura(), alertas);
		if (c.edadAnios() >= EDAD_UMBRALES_ADULTO) {
			evaluarPresion(s.presionSistolica(), s.presionDiastolica(), alertas);
			evaluarFrecuenciaCardiaca(s.frecuenciaCardiaca(), alertas);
			evaluarFrecuenciaRespiratoria(s.frecuenciaRespiratoria(), alertas);
		}
		BigDecimal imc = imc(s.peso(), s.talla());
		if (imc != null && c.edadAnios() >= 18) {
			evaluarImc(imc, alertas);
		}
		return new Evaluacion(List.copyOf(alertas), imc, prioridadSugerida(alertas, c));
	}

	static Prioridad prioridadSugerida(List<Alerta> alertas, Condiciones c) {
		if (alertas.stream().anyMatch(a -> a.getSeveridad() == Severidad.CRITICA)) {
			return Prioridad.URGENTE;
		}
		boolean preferente = c.gestante() || c.discapacidad() || c.edadAnios() >= EDAD_ADULTO_MAYOR
				|| c.edadAnios() < EDAD_UMBRALES_ADULTO;
		return preferente ? Prioridad.PREFERENTE : Prioridad.NORMAL;
	}

	/** IMC = peso (kg) / talla (m)², con un decimal. Null si no hay talla. */
	static BigDecimal imc(BigDecimal peso, BigDecimal talla) {
		if (peso == null || talla == null || talla.signum() <= 0) {
			return null;
		}
		BigDecimal metros = talla.divide(BigDecimal.valueOf(100));
		return peso.divide(metros.multiply(metros), 1, RoundingMode.HALF_UP);
	}

	private static void evaluarSaturacion(int sat, List<Alerta> alertas) {
		if (sat < 90) {
			alertas.add(critica("SATURACION_MUY_BAJA", "Saturación de O₂ muy baja: " + sat + " %"));
		}
		else if (sat < 92) {
			alertas.add(advertencia("SATURACION_BAJA", "Saturación de O₂ baja: " + sat + " %"));
		}
	}

	private static void evaluarTemperatura(BigDecimal temp, List<Alerta> alertas) {
		if (temp.compareTo(BigDecimal.valueOf(40)) >= 0) {
			alertas.add(critica("HIPERPIREXIA", "Fiebre muy alta: " + temp + " °C"));
		}
		else if (temp.compareTo(BigDecimal.valueOf(38)) >= 0) {
			alertas.add(advertencia("FIEBRE", "Fiebre: " + temp + " °C"));
		}
		else if (temp.compareTo(BigDecimal.valueOf(35)) < 0) {
			alertas.add(critica("HIPOTERMIA", "Hipotermia: " + temp + " °C"));
		}
	}

	private static void evaluarPresion(Integer sistolica, Integer diastolica, List<Alerta> alertas) {
		if (sistolica == null || diastolica == null) {
			return;
		}
		String pa = sistolica + "/" + diastolica + " mmHg";
		if (sistolica >= 180 || diastolica >= 110) {
			alertas.add(critica("CRISIS_HIPERTENSIVA", "Presión arterial muy elevada: " + pa));
		}
		else if (sistolica >= 140 || diastolica >= 90) {
			alertas.add(advertencia("PRESION_ELEVADA", "Presión arterial elevada: " + pa));
		}
		else if (sistolica < 80) {
			alertas.add(critica("HIPOTENSION_SEVERA", "Presión arterial muy baja: " + pa));
		}
		else if (sistolica < 90) {
			alertas.add(advertencia("HIPOTENSION", "Presión arterial baja: " + pa));
		}
	}

	private static void evaluarFrecuenciaCardiaca(int fc, List<Alerta> alertas) {
		if (fc > 130 || fc < 40) {
			alertas.add(critica("FC_CRITICA", "Frecuencia cardiaca de riesgo: " + fc + " lpm"));
		}
		else if (fc > 100) {
			alertas.add(advertencia("TAQUICARDIA", "Taquicardia: " + fc + " lpm"));
		}
		else if (fc < 50) {
			alertas.add(advertencia("BRADICARDIA", "Bradicardia: " + fc + " lpm"));
		}
	}

	private static void evaluarFrecuenciaRespiratoria(Integer fr, List<Alerta> alertas) {
		if (fr == null) {
			return;
		}
		if (fr > 30 || fr < 8) {
			alertas.add(critica("FR_CRITICA", "Frecuencia respiratoria de riesgo: " + fr + " rpm"));
		}
		else if (fr > 24) {
			alertas.add(advertencia("TAQUIPNEA", "Taquipnea: " + fr + " rpm"));
		}
		else if (fr < 10) {
			alertas.add(advertencia("BRADIPNEA", "Bradipnea: " + fr + " rpm"));
		}
	}

	private static void evaluarImc(BigDecimal imc, List<Alerta> alertas) {
		if (imc.compareTo(BigDecimal.valueOf(30)) >= 0) {
			alertas.add(advertencia("OBESIDAD", "IMC en rango de obesidad: " + imc));
		}
		else if (imc.compareTo(BigDecimal.valueOf(18.5)) < 0) {
			alertas.add(advertencia("BAJO_PESO", "IMC en rango de bajo peso: " + imc));
		}
	}

	private static Alerta critica(String codigo, String mensaje) {
		return new Alerta(codigo, Severidad.CRITICA, mensaje);
	}

	private static Alerta advertencia(String codigo, String mensaje) {
		return new Alerta(codigo, Severidad.ADVERTENCIA, mensaje);
	}

}
