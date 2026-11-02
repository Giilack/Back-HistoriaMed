package com.historiamed.backend.importacion.dto;

import java.util.List;

import com.historiamed.backend.extraccion.ExtraccionIaService.DatoAprobado;
import com.historiamed.backend.paciente.dto.PacienteRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param paciente datos personales revisados por Admisión; un paciente ya registrado no se modifica
 * @param datos datos clínicos tal como quedaron tras la revisión: lo que no se envía no pasa a la historia
 * @param pacienteId paciente elegido por Admisión entre los parecidos; si es null, se ubica por documento o se crea
 * @param confirmarCoincidencia Admisión confirma que no mezcla ni duplica a una persona
 * @param confirmarUnPaciente Admisión confirma que el documento es de una sola persona
 */
public record AprobacionRequest(@NotNull @Valid PacienteRequest paciente,
		@NotNull @Size(max = 200) List<@Valid DatoAprobado> datos, Long pacienteId, Boolean confirmarCoincidencia,
		Boolean confirmarUnPaciente) {
}
