package com.historiamed.backend.antecedente;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.historiamed.backend.antecedente.dto.AntecedenteResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Antecedentes del paciente. Los consultan TRIAJE y MEDICO (dato clínico: ADMISION y ADMIN no acceden); solo el
 * MEDICO los inactiva.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('TRIAJE', 'MEDICO')")
@RequiredArgsConstructor
public class AntecedenteController {

	private final AntecedenteService service;

	public record InactivacionRequest(@NotBlank @Size(max = 200) String motivo) {
	}

	@GetMapping("/pacientes/{pacienteId}/antecedentes")
	public List<AntecedenteResponse> listar(@PathVariable Long pacienteId) {
		return service.listar(pacienteId);
	}

	@PatchMapping("/antecedentes/{id}/inactivar")
	@PreAuthorize("hasRole('MEDICO')")
	public AntecedenteResponse inactivar(@PathVariable Long id, @Valid @RequestBody InactivacionRequest req) {
		return service.inactivar(id, req.motivo());
	}

}
