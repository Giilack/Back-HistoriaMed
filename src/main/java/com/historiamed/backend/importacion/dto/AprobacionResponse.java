package com.historiamed.backend.importacion.dto;

/** @param pacienteNuevo si la aprobación creó al paciente o agregó la historia a uno que ya existía */
public record AprobacionResponse(Long pacienteId, String numeroHc, boolean pacienteNuevo, int datosRegistrados) {
}
