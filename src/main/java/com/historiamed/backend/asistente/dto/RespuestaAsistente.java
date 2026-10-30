package com.historiamed.backend.asistente.dto;

import java.util.List;

/**
 * Respuesta del asistente clínico.
 *
 * @param respuesta texto para el médico
 * @param fuentes registros de la historia en los que se basa (por ejemplo «Atención del 12/03/2026, Dr. X»)
 * @param sugerencia si la respuesta propone un tratamiento: se muestra como apoyo, no reemplaza el criterio médico
 * @param advertencias alertas deterministas de alergias sobre los medicamentos que menciona la sugerencia
 * @param modelo modelo de IA que respondió
 */
public record RespuestaAsistente(String respuesta, List<Fuente> fuentes, boolean sugerencia,
		List<String> advertencias, String modelo) {

	public record Fuente(String id, String etiqueta) {
	}

}
