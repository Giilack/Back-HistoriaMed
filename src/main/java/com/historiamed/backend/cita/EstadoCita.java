package com.historiamed.backend.cita;

import java.util.EnumSet;
import java.util.Set;

/**
 * Flujo de una cita o turno (plan.md, sección 4). Solo se avanza al siguiente paso: no se salta el triaje.
 *
 * <pre>
 * PROGRAMADA ─► EN_ESPERA_TRIAJE ─► EN_ESPERA_CONSULTA ─► EN_CONSULTA ─► ATENDIDO
 *     │                │                    │
 *     ├─► CANCELADA    └────────────────────┴─► NO_SE_PRESENTO
 *     └─► NO_SE_PRESENTO
 * </pre>
 */
public enum EstadoCita {

	PROGRAMADA,
	EN_ESPERA_TRIAJE,
	EN_ESPERA_CONSULTA,
	EN_CONSULTA,
	ATENDIDO,
	CANCELADA,
	NO_SE_PRESENTO;

	/** Estados en los que la cita sigue "viva" (ocupa un horario y un turno). */
	public static final Set<EstadoCita> ACTIVOS = EnumSet.of(PROGRAMADA, EN_ESPERA_TRIAJE, EN_ESPERA_CONSULTA,
			EN_CONSULTA);

	public Set<EstadoCita> siguientes() {
		return switch (this) {
			case PROGRAMADA -> EnumSet.of(EN_ESPERA_TRIAJE, CANCELADA, NO_SE_PRESENTO);
			case EN_ESPERA_TRIAJE -> EnumSet.of(EN_ESPERA_CONSULTA, NO_SE_PRESENTO);
			case EN_ESPERA_CONSULTA -> EnumSet.of(EN_CONSULTA, NO_SE_PRESENTO);
			case EN_CONSULTA -> EnumSet.of(ATENDIDO);
			case ATENDIDO, CANCELADA, NO_SE_PRESENTO -> EnumSet.noneOf(EstadoCita.class);
		};
	}

	public boolean puedePasarA(EstadoCita nuevo) {
		return siguientes().contains(nuevo);
	}

	public boolean esActivo() {
		return ACTIVOS.contains(this);
	}

}
