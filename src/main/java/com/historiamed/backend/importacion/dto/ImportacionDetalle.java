package com.historiamed.backend.importacion.dto;

import java.util.List;

import com.historiamed.backend.extraccion.ExtraccionIaService.DatosPersonalesIa;
import com.historiamed.backend.extraccion.dto.ItemRequest;

/**
 * Lo que propuso la IA.
 *
 * @param datos datos clínicos; la posición en la lista es lo que se acepta al aprobar
 * @param existente paciente con el mismo documento, si ya está registrado
 */
public record ImportacionDetalle(ImportacionResponse importacion, DatosPersonalesIa paciente, List<ItemRequest> datos,
		PacienteExistente existente) {

	/** @param coincideNombre si el nombre del documento es el del paciente registrado */
	public record PacienteExistente(Long id, String numeroHc, String nombreCompleto, boolean coincideNombre) {
	}

}
