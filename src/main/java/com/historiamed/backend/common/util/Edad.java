package com.historiamed.backend.common.util;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

/**
 * Cálculo de edad en la zona horaria del establecimiento.
 */
public final class Edad {

	public static final ZoneId ZONA = ZoneId.of("America/Lima");

	private Edad() {
	}

	public static LocalDate hoy() {
		return LocalDate.now(ZONA);
	}

	public static int anios(LocalDate nacimiento) {
		return Period.between(nacimiento, hoy()).getYears();
	}

	/**
	 * Edad legible como en la práctica clínica: "34 años", "8 meses" (lactantes) o "12 días" (recién nacidos).
	 */
	public static String texto(LocalDate nacimiento, LocalDate hoy) {
		Period p = Period.between(nacimiento, hoy);
		if (p.getYears() >= 1) {
			return plural(p.getYears(), "año", "años");
		}
		if (p.getMonths() >= 1) {
			return plural(p.getMonths(), "mes", "meses");
		}
		return plural(p.getDays(), "día", "días");
	}

	public static String texto(LocalDate nacimiento) {
		return texto(nacimiento, hoy());
	}

	private static String plural(int n, String singular, String plural) {
		return n + " " + (n == 1 ? singular : plural);
	}

}
