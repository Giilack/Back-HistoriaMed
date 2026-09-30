package com.historiamed.backend.documento;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.util.Tiempo;

/**
 * Almacenamiento dentro de PostgreSQL (tabla {@code archivos}). Se activa con
 * {@code historiamed.almacenamiento.tipo=bd} (variable ALMACENAMIENTO_TIPO). Pensado para la nube gratuita, donde
 * el disco del servidor se borra en cada reinicio: los documentos quedan junto a los demás datos y entran en el
 * mismo respaldo. Como cada archivo pesa como máximo 10 MB, se lee completo en memoria.
 */
@Component
@ConditionalOnProperty(name = "historiamed.almacenamiento.tipo", havingValue = "bd")
public class AlmacenamientoBaseDatos implements AlmacenamientoService {

	private static final DateTimeFormatter CARPETA_MES = DateTimeFormatter.ofPattern("yyyy/MM");

	private final JdbcTemplate jdbc;

	public AlmacenamientoBaseDatos(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String guardar(byte[] contenido, String extension) {
		// Mismo formato de clave que el almacenamiento local: la clave no revela nada del paciente
		String clave = Tiempo.hoy().format(CARPETA_MES) + "/" + UUID.randomUUID() + "." + extension;
		jdbc.update("INSERT INTO archivos (clave, contenido) VALUES (?, ?)", clave, contenido);
		return clave;
	}

	@Override
	public Resource obtener(String clave) {
		List<byte[]> filas = jdbc.query("SELECT contenido FROM archivos WHERE clave = ?",
				(rs, n) -> rs.getBytes("contenido"), clave);
		if (filas.isEmpty()) {
			throw new RecursoNoEncontradoException("Archivo del documento", clave);
		}
		return new ByteArrayResource(filas.get(0));
	}

}
