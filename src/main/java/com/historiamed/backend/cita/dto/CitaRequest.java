package com.historiamed.backend.cita.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Programar o reprogramar una cita. Al reprogramar, el paciente no cambia (se ignora pacienteId).
 */
public record CitaRequest(
		@NotNull Long pacienteId,
		@NotNull Long medicoId,
		@NotNull Long consultorioId,
		@NotNull LocalDate fecha,
		@NotNull LocalTime hora,
		@Size(max = 200) String motivo) {
}
