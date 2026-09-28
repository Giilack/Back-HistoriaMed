package com.historiamed.backend.triaje.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.historiamed.backend.cita.Prioridad;
import com.historiamed.backend.triaje.Triaje;
import com.historiamed.backend.usuario.Usuario;

public record TriajeResponse(Long id, Long citaId, Long pacienteId, Instant fechaHora, String registradoPor,
		String motivoConsulta, Integer presionSistolica, Integer presionDiastolica, Integer frecuenciaCardiaca,
		Integer frecuenciaRespiratoria, BigDecimal temperatura, Integer saturacion, BigDecimal peso,
		BigDecimal talla, BigDecimal imc, BigDecimal perimetroAbdominal, boolean gestante, boolean discapacidad,
		Prioridad prioridadSugerida, Prioridad prioridad, String justificacionPrioridad, String observaciones,
		List<AlertaResponse> alertas) {

	public static TriajeResponse de(Triaje t) {
		Usuario u = t.getRegistradoPor();
		return new TriajeResponse(t.getId(), t.getCita().getId(), t.getPaciente().getId(), t.getFechaHora(),
				u.getApellidos() + ", " + u.getNombres(), t.getMotivoConsulta(), t.getPresionSistolica(),
				t.getPresionDiastolica(), t.getFrecuenciaCardiaca(), t.getFrecuenciaRespiratoria(),
				t.getTemperatura(), t.getSaturacion(), t.getPeso(), t.getTalla(), t.getImc(),
				t.getPerimetroAbdominal(), t.isGestante(), t.isDiscapacidad(), t.getPrioridadSugerida(),
				t.getPrioridad(), t.getJustificacionPrioridad(), t.getObservaciones(),
				t.getAlertas().stream().map(AlertaResponse::de).toList());
	}

}
