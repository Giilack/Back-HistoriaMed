package com.historiamed.backend.documento;

import java.util.Optional;

/**
 * Formatos aceptados, identificados por sus primeros bytes ("firma" del archivo) y no por la extensión ni por el
 * tipo que declara el navegador, que se pueden falsificar.
 */
public enum FormatoArchivo {

	PDF("application/pdf", "pdf"),
	JPEG("image/jpeg", "jpg"),
	PNG("image/png", "png");

	private final String contentType;

	private final String extension;

	FormatoArchivo(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}

	public String contentType() {
		return contentType;
	}

	public String extension() {
		return extension;
	}

	public static Optional<FormatoArchivo> detectar(byte[] b) {
		if (empiezaCon(b, '%', 'P', 'D', 'F', '-')) {
			return Optional.of(PDF);
		}
		if (empiezaCon(b, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
			return Optional.of(PNG);
		}
		if (empiezaCon(b, 0xFF, 0xD8, 0xFF)) {
			return Optional.of(JPEG);
		}
		return Optional.empty();
	}

	private static boolean empiezaCon(byte[] b, int... firma) {
		if (b.length < firma.length) {
			return false;
		}
		for (int i = 0; i < firma.length; i++) {
			if ((b[i] & 0xFF) != firma[i]) {
				return false;
			}
		}
		return true;
	}

}
