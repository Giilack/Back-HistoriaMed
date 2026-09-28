package com.historiamed.backend.triaje.dto;

import java.math.BigDecimal;
import java.util.List;

import com.historiamed.backend.cita.Prioridad;
import com.historiamed.backend.triaje.EvaluadorTriaje;

/**
 * Vista previa del triaje (sin guardar): alertas, IMC y prioridad sugerida.
 */
public record EvaluacionResponse(List<AlertaResponse> alertas, BigDecimal imc, Prioridad prioridadSugerida) {

	public static EvaluacionResponse de(EvaluadorTriaje.Evaluacion e) {
		return new EvaluacionResponse(e.alertas().stream().map(AlertaResponse::de).toList(), e.imc(),
				e.prioridadSugerida());
	}

}
