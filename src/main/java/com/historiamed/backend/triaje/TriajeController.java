package com.historiamed.backend.triaje;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.historiamed.backend.triaje.dto.EvaluacionResponse;
import com.historiamed.backend.triaje.dto.TriajeRequest;
import com.historiamed.backend.triaje.dto.TriajeResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Triaje. Lo registran TRIAJE y MEDICO; ADMISION y ADMIN no acceden a datos clínicos (plan.md, principio P1).
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('TRIAJE', 'MEDICO')")
@RequiredArgsConstructor
public class TriajeController {

	private final TriajeService service;

	/** Vista previa mientras se llena el formulario: alertas, IMC y prioridad sugerida. No guarda nada. */
	@PostMapping("/citas/{citaId}/triaje/evaluacion")
	public EvaluacionResponse evaluar(@PathVariable Long citaId, @Valid @RequestBody TriajeRequest req) {
		return service.evaluar(citaId, req);
	}

	@PostMapping("/citas/{citaId}/triaje")
	@ResponseStatus(HttpStatus.CREATED)
	public TriajeResponse registrar(@PathVariable Long citaId, @Valid @RequestBody TriajeRequest req) {
		return service.registrar(citaId, req);
	}

	@GetMapping("/citas/{citaId}/triaje")
	public TriajeResponse verPorCita(@PathVariable Long citaId) {
		return service.verPorCita(citaId);
	}

	@GetMapping("/pacientes/{pacienteId}/triajes")
	public List<TriajeResponse> historial(@PathVariable Long pacienteId) {
		return service.historial(pacienteId);
	}

}
