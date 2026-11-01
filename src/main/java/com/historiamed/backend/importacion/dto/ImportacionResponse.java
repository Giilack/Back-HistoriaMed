package com.historiamed.backend.importacion.dto;

import java.time.Instant;

import com.historiamed.backend.importacion.Importacion;

/** Fila de la lista: sin datos del paciente, que están cifrados hasta abrir la revisión. */
public record ImportacionResponse(Long id, String nombreArchivo, Importacion.Estado estado, String error,
		Long tamanioBytes, Instant creadoEn, Long pacienteId, String motivoRechazo) {

	public static ImportacionResponse de(Importacion i) {
		return new ImportacionResponse(i.getId(), i.getNombreOriginal(), i.getEstado(), i.getError(), i.getTamanioBytes(),
				i.getCreadoEn(), i.getPacienteId(), i.getMotivoRechazo());
	}

}
