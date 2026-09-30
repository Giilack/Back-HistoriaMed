package com.historiamed.backend.antecedente;

public enum TipoAntecedente {

	/** Enfermedades o condiciones del propio paciente (por ejemplo, "asma desde la infancia"). */
	PERSONAL,
	/** Enfermedades de familiares directos (por ejemplo, "madre con diabetes"). */
	FAMILIAR,
	/** Cirugías y procedimientos. */
	QUIRURGICO,
	/** Diagnóstico hecho fuera de este establecimiento, codificado con CIE-10. */
	DIAGNOSTICO_PREVIO,
	/** Medicamento que el paciente ya viene tomando. */
	MEDICACION_HABITUAL,
	OTRO

}
