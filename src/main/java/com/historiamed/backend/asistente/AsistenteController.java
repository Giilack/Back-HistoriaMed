package com.historiamed.backend.asistente;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.historiamed.backend.asistente.dto.PreguntaAsistenteRequest;
import com.historiamed.backend.asistente.dto.RespuestaAsistente;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Asistente clínico (HU010, plan.md 5.6): el médico pregunta sobre la historia del paciente que tiene abierto. Solo
 * el MEDICO, porque responde con la historia clínica completa.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('MEDICO')")
@RequiredArgsConstructor
public class AsistenteController {

	private final AsistenteService service;

	@PostMapping("/pacientes/{pacienteId}/asistente")
	public RespuestaAsistente preguntar(@PathVariable Long pacienteId, @Valid @RequestBody PreguntaAsistenteRequest req) {
		return service.preguntar(pacienteId, req);
	}

}
