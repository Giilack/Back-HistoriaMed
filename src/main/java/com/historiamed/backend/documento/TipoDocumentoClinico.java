package com.historiamed.backend.documento;

/**
 * Tipo de documento clínico. Hoy lo elige quien lo sube; cuando se active la IA se sugerirá automáticamente
 * (plan.md, sección 5.5).
 */
public enum TipoDocumentoClinico {

	LABORATORIO,
	RECETA,
	INFORME_MEDICO,
	/** Resumen de una hospitalización al alta. */
	EPICRISIS,
	/** Radiografías, ecografías, tomografías y sus informes. */
	IMAGENOLOGIA,
	/** Hoja de referencia o contrarreferencia de otro establecimiento. */
	REFERENCIA,
	OTRO

}
