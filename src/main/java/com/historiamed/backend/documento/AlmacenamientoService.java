package com.historiamed.backend.documento;

import org.springframework.core.io.Resource;

/**
 * Dónde se guardan los archivos. Se elige con {@code historiamed.almacenamiento.tipo}: {@code local} (disco,
 * {@link AlmacenamientoLocal}) o {@code bd} (PostgreSQL, {@link AlmacenamientoBaseDatos}, para la nube). Cambiar
 * de uno a otro, o agregar otro como Cloudflare R2, no toca el resto del sistema (plan.md, sección 6.2).
 */
public interface AlmacenamientoService {

	/**
	 * Guarda el contenido y devuelve la clave interna con la que se recupera. La clave la genera el sistema;
	 * nunca se usa el nombre de archivo que envió el usuario.
	 */
	String guardar(byte[] contenido, String extension);

	Resource obtener(String clave);

}
