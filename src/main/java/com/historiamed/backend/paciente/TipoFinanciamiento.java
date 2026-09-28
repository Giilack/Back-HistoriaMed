package com.historiamed.backend.paciente;

/**
 * Cómo se financia la atención del paciente. Ver plan.md, sección 5.2.
 */
public enum TipoFinanciamiento {

	/** Seguro Integral de Salud (público). La afiliación es de la persona. */
	SIS,
	ESSALUD,
	/** EPS o seguro privado. */
	PRIVADO,
	/** Sin seguro: paga la atención. */
	PARTICULAR;

	public boolean tieneSeguro() {
		return this != PARTICULAR;
	}

}
