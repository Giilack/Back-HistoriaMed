package com.historiamed.backend.usuario;

/**
 * Roles del sistema. Cada uno corresponde a un proceso real del establecimiento (ver plan.md, sección 3).
 */
public enum Rol {

	/** Administra usuarios, catálogos y auditoría. No accede al contenido clínico. */
	ADMIN,

	/** Registra pacientes, financiamiento, citas y llegada a la cola. */
	ADMISION,

	/** Registra signos vitales, motivo de consulta y prioridad. */
	TRIAJE,

	/** Atiende: ve la historia completa, diagnostica, receta y cierra la atención. */
	MEDICO

}
