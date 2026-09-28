package com.historiamed.backend.atencion;

public enum EstadoAtencion {

	/** El médico la está llenando: puede guardar borradores. */
	EN_CURSO,
	/** Firmada: inmutable (plan.md, principio P3). Solo admite adendas. */
	CERRADA

}
