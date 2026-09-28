package com.historiamed.backend.common.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Fecha y hora en la zona horaria del establecimiento (no la del servidor, que puede estar en UTC).
 */
public final class Tiempo {

	public static final ZoneId ZONA = ZoneId.of("America/Lima");

	private Tiempo() {
	}

	public static LocalDate hoy() {
		return LocalDate.now(ZONA);
	}

	public static LocalTime ahora() {
		return LocalTime.now(ZONA);
	}

}
