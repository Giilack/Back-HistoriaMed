package com.historiamed.backend.cita;

/**
 * Prioridad de atención asignada en triaje (plan.md, sección 5.3). El orden importa: de menor a mayor.
 */
public enum Prioridad {

	NORMAL,
	/** Gestantes, adultos mayores, niños y personas con discapacidad. */
	PREFERENTE,
	/** Signos vitales críticos: se sugiere evaluar la derivación a emergencia. */
	URGENTE;

	public boolean esMenorQue(Prioridad otra) {
		return compareTo(otra) < 0;
	}

}
