package com.historiamed.backend.atencion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.historiamed.backend.catalogo.Medicamento;
import com.historiamed.backend.common.util.Texto;

/**
 * Compara un medicamento con las alergias activas del paciente. Reglas fijas, sin IA (plan.md, principio P8):
 * <ol>
 * <li>La alergia menciona un principio activo del medicamento ("amoxicilina" → Amoxicilina + ácido clavulánico).</li>
 * <li>La alergia menciona el grupo farmacológico ("penicilina", "sulfas", "AINE" → todos los de ese grupo).</li>
 * <li>Reacción cruzada conocida: alergia a penicilinas y el medicamento es una cefalosporina.</li>
 * </ol>
 * Es deliberadamente conservador: ante la duda, avisa. El médico decide si confirma con una justificación.
 */
@Component
public class VerificadorAlergias {

	/** Palabras más cortas se ignoran al comparar ("a", "la", "de", "g"). */
	private static final int LONGITUD_MINIMA_PALABRA = 3;

	/** Cómo suele escribirse cada grupo en una alergia. */
	private static final Map<String, Set<String>> ALIAS_GRUPO = Map.of(
			"PENICILINAS", Set.of("penicilina", "penicilinas", "betalactamico", "betalactamicos"),
			"CEFALOSPORINAS", Set.of("cefalosporina", "cefalosporinas"),
			"SULFONAMIDAS", Set.of("sulfa", "sulfas", "sulfonamida", "sulfonamidas"),
			"AINES", Set.of("aine", "aines", "antiinflamatorio", "antiinflamatorios"),
			"MACROLIDOS", Set.of("macrolido", "macrolidos"),
			"QUINOLONAS", Set.of("quinolona", "quinolonas", "fluoroquinolona", "fluoroquinolonas"),
			"CORTICOIDES", Set.of("corticoide", "corticoides", "corticosteroide", "corticosteroides"),
			"OPIOIDES", Set.of("opioide", "opioides", "opiaceo", "opiaceos"));

	/** Nombres comunes o comerciales frecuentes → principio activo. */
	private static final Map<String, String> SINONIMOS = Map.of("aspirina", "acido acetilsalicilico", "cotrimoxazol",
			"sulfametoxazol", "bactrim", "sulfametoxazol", "dipirona", "metamizol");

	/** Alergia activa del paciente (solo lo necesario para comparar). */
	public record AlergiaActiva(String sustancia, String gravedad) {
	}

	public record Coincidencia(AlergiaActiva alergia, String motivo) {
	}

	public List<Coincidencia> verificar(Medicamento m, List<AlergiaActiva> alergias) {
		List<String> componentes = Arrays.stream(m.getPrincipioActivo().split("\\+")).map(Texto::normalizar).toList();
		List<Coincidencia> coincidencias = new ArrayList<>();
		for (AlergiaActiva a : alergias) {
			String motivo = motivo(a, m, componentes);
			if (motivo != null) {
				coincidencias.add(new Coincidencia(a, motivo));
			}
		}
		return coincidencias;
	}

	private static String motivo(AlergiaActiva a, Medicamento m, List<String> componentes) {
		String alergia = Texto.normalizar(a.sustancia());
		String equivalente = SINONIMOS.getOrDefault(alergia, alergia);

		for (String componente : componentes) {
			if (coincide(equivalente, componente)) {
				return "contiene " + componente;
			}
		}
		String grupo = m.getGrupo();
		if (grupo != null && mencionaGrupo(alergia, grupo)) {
			return "pertenece al grupo " + grupo;
		}
		if ("CEFALOSPORINAS".equals(grupo) && mencionaGrupo(alergia, "PENICILINAS")) {
			return "posible reacción cruzada entre penicilinas y cefalosporinas";
		}
		return null;
	}

	/**
	 * Coincide si todas las palabras de uno están en el otro, como palabras completas: "penicilina" en "penicilina
	 * g benzatinica", o "amoxicilina" en "alergia a la amoxicilina". No compara subcadenas, para que "sulfa" no
	 * coincida con "sulfato ferroso" (la alergia a sulfas se detecta por el grupo SULFONAMIDAS).
	 */
	private static boolean coincide(String alergia, String componente) {
		Set<String> deAlergia = palabras(alergia);
		Set<String> deComponente = palabras(componente);
		if (deAlergia.isEmpty() || deComponente.isEmpty()) {
			return false;
		}
		return deAlergia.containsAll(deComponente) || deComponente.containsAll(deAlergia);
	}

	/** Palabras significativas (se descartan "a", "la", "de", "g"...). */
	private static Set<String> palabras(String texto) {
		return Arrays.stream(texto.split("[^a-z0-9]+"))
			.filter(p -> p.length() >= LONGITUD_MINIMA_PALABRA)
			.collect(Collectors.toSet());
	}

	private static boolean mencionaGrupo(String alergia, String grupo) {
		Set<String> palabras = Set.of(alergia.split("[^a-z]+"));
		return ALIAS_GRUPO.getOrDefault(grupo, Set.of()).stream().anyMatch(palabras::contains);
	}

}
