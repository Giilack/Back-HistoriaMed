package com.historiamed.backend.documento;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.util.Tiempo;
import com.historiamed.backend.config.HistoriaMedProperties;

/**
 * Almacenamiento en disco local: {directorio}/2026/09/uuid.pdf. Es el predeterminado
 * ({@code historiamed.almacenamiento.tipo=local}): gratuito y suficiente para desarrollo y sustentación. La
 * carpeta está en .gitignore (contiene datos de pacientes).
 */
@Component
@ConditionalOnProperty(name = "historiamed.almacenamiento.tipo", havingValue = "local", matchIfMissing = true)
public class AlmacenamientoLocal implements AlmacenamientoService {

	private static final DateTimeFormatter CARPETA_MES = DateTimeFormatter.ofPattern("yyyy/MM");

	private final Path base;

	public AlmacenamientoLocal(HistoriaMedProperties properties) {
		this.base = Path.of(properties.almacenamiento().directorio()).toAbsolutePath().normalize();
	}

	@Override
	public String guardar(byte[] contenido, String extension) {
		String clave = Tiempo.hoy().format(CARPETA_MES) + "/" + UUID.randomUUID() + "." + extension;
		Path destino = resolver(clave);
		try {
			Files.createDirectories(destino.getParent());
			// CREATE_NEW: nunca sobrescribe un archivo existente
			Files.write(destino, contenido, StandardOpenOption.CREATE_NEW);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("No se pudo guardar el documento", ex);
		}
		return clave;
	}

	@Override
	public Resource obtener(String clave) {
		Path archivo = resolver(clave);
		if (!Files.isRegularFile(archivo)) {
			throw new RecursoNoEncontradoException("Archivo del documento", clave);
		}
		return new PathResource(archivo);
	}

	/** Resuelve la clave dentro de la carpeta base, impidiendo salir de ella ("../"). */
	private Path resolver(String clave) {
		Path ruta = base.resolve(clave).normalize();
		if (!ruta.startsWith(base)) {
			throw new IllegalArgumentException("Clave de almacenamiento inválida");
		}
		return ruta;
	}

}
