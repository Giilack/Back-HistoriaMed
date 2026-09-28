package com.historiamed.backend.cita.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Paciente que llega sin cita: entra directamente a la cola de triaje del día.
 */
public record LlegadaSinCitaRequest(
		@NotNull Long pacienteId,
		@NotNull Long medicoId,
		@NotNull Long consultorioId,
		@Size(max = 200) String motivo) {
}
