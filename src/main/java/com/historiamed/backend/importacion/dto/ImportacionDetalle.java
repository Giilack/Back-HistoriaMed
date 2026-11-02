package com.historiamed.backend.importacion.dto;

import java.util.List;

import com.historiamed.backend.extraccion.ExtraccionIaService.DatosPersonalesIa;
import com.historiamed.backend.extraccion.dto.ItemRequest;

/**
 * Lo que propuso la IA.
 *
 * @param datos datos clínicos
 * @param existente paciente con el mismo documento, si ya está registrado
 * @param parecidos pacientes con el mismo apellido y fecha de nacimiento: posibles duplicados
 * @param variosPacientes la IA detectó a más de una persona en el documento
 * @param datosOmitidos datos que superaron el máximo por documento y no se muestran
 */
public record ImportacionDetalle(ImportacionResponse importacion, DatosPersonalesIa paciente, List<ItemRequest> datos,
		PacienteExistente existente, List<PacienteExistente> parecidos, boolean variosPacientes, int datosOmitidos) {

	/** @param coincideNombre si el nombre del documento es el del paciente registrado */
	public record PacienteExistente(Long id, String numeroHc, String nombreCompleto, boolean coincideNombre) {
	}

}
