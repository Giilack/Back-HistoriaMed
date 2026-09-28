package com.historiamed.backend.alergia.dto;

import com.historiamed.backend.alergia.GravedadAlergia;
import com.historiamed.backend.alergia.TipoAlergia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param sustancia medicamento, alimento o agente (por ejemplo, "Penicilina", "Mariscos", "Látex")
 * @param reaccion lo que le ocurre (por ejemplo, "urticaria", "dificultad para respirar")
 */
public record AlergiaRequest(
		@NotNull TipoAlergia tipo,
		@NotBlank @Size(max = 100) String sustancia,
		@Size(max = 200) String reaccion,
		@NotNull GravedadAlergia gravedad) {
}
