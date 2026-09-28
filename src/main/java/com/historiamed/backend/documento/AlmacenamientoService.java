package com.historiamed.backend.documento;

import org.springframework.core.io.Resource;

/**
 * Dónde se guardan los archivos. Hoy: disco local ({@link AlmacenamientoLocal}). Para la nube se agregará otra
 * implementación (Cloudflare R2, compatible con S3) sin cambiar el resto del sistema (plan.md, sección 6.2).
 */
public interface AlmacenamientoService {

	/**
	 * Guarda el contenido y devuelve la clave interna con la que se recupera. La clave la genera el sistema;
	 * nunca se usa el nombre de archivo que envió el usuario.
	 */
	String guardar(byte[] contenido, String extension);

	Resource obtener(String clave);

}
