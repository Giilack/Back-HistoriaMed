package com.historiamed.backend.importacion.dto;

import java.util.Set;

import com.historiamed.backend.paciente.dto.PacienteRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * @param paciente datos personales revisados por Admisión; un paciente ya registrado no se modifica
 * @param aceptados posiciones de los datos clínicos que pasan a la historia
 * @param confirmarCoincidencia Admisión confirma que es la misma persona aunque el nombre no coincida
 */
public record AprobacionRequest(@NotNull @Valid PacienteRequest paciente, @NotNull Set<Integer> aceptados,
		boolean confirmarCoincidencia) {
}
