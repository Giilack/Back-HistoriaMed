package com.historiamed.backend.ia;

import java.util.Map;

/**
 * Servicio de IA que lee un documento y responde en JSON (plan.md, sección 5.5). Hoy lo implementa
 * {@link ProveedorGemini}; cambiar de proveedor (por ejemplo, a un modelo local con Ollama) es escribir otra
 * implementación, sin tocar el resto del sistema.
 * <p>
 * La IA solo <b>propone</b>: lo que devuelve se guarda como sugerencia y un médico lo valida (principio P2).
 */
public interface ProveedorIA {

	/** Si está configurado (por ejemplo, si hay clave de API). Sin IA, el sistema sigue funcionando a mano. */
	boolean disponible();

	/** Nombre del modelo que se usa, para dejarlo registrado junto a la propuesta. */
	String modelo();

	/**
	 * Envía el documento con las instrucciones y devuelve el texto JSON de la respuesta, que cumple el esquema.
	 *
	 * @param documento contenido del archivo (PDF o imagen)
	 * @param mimeType tipo del archivo, por ejemplo {@code application/pdf}
	 * @param instrucciones qué extraer y cómo
	 * @param esquema estructura que debe tener el JSON de respuesta
	 * @throws ErrorIaException si el servicio falla, rechaza la solicitud o no responde a tiempo
	 */
	String extraerJson(byte[] documento, String mimeType, String instrucciones, Map<String, Object> esquema);

	/**
	 * Envía solo texto (instrucciones y contenido) y devuelve el texto JSON de la respuesta, que cumple el esquema. Lo
	 * usa el asistente clínico (plan.md, sección 5.6).
	 *
	 * @param instrucciones reglas que debe seguir la IA
	 * @param contenido datos y pregunta sobre los que responde
	 * @param esquema estructura que debe tener el JSON de respuesta
	 * @throws ErrorIaException si el servicio falla, rechaza la solicitud o no responde a tiempo
	 */
	String responderJson(String instrucciones, String contenido, Map<String, Object> esquema);

}
