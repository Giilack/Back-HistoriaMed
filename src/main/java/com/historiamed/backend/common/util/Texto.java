package com.historiamed.backend.common.util;

import java.text.Normalizer;
import java.util.Locale;

public final class Texto {

	private Texto() {
	}

	/** Minúsculas, sin tildes y con espacios simples: para comparar textos escritos por personas. */
	public static String normalizar(String texto) {
		if (texto == null) {
			return "";
		}
		String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return sinTildes.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
	}

}
