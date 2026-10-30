package com.historiamed.backend.asistente.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pregunta del médico al asistente sobre el paciente abierto, con los últimos turnos de la conversación (la
 * conversación no se guarda en el servidor: la envía el navegador en cada pregunta).
 */
public record PreguntaAsistenteRequest(@NotBlank @Size(max = 500) String pregunta,
		@Size(max = 6) List<@Valid Turno> historial) {

	public enum Autor {
		MEDICO, ASISTENTE
	}

	public record Turno(@NotNull Autor autor, @NotBlank @Size(max = 2000) String texto) {
	}

}
