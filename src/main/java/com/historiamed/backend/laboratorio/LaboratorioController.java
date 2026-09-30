package com.historiamed.backend.laboratorio;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.historiamed.backend.laboratorio.dto.ResultadoLaboratorioResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Resultados de laboratorio del paciente. Los consultan TRIAJE y MEDICO (dato clínico: ADMISION y ADMIN no
 * acceden); solo el MEDICO los inactiva.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('TRIAJE', 'MEDICO')")
@RequiredArgsConstructor
public class LaboratorioController {

	private final LaboratorioService service;

	public record InactivacionRequest(@NotBlank @Size(max = 200) String motivo) {
	}

	@GetMapping("/pacientes/{pacienteId}/resultados-laboratorio")
	public List<ResultadoLaboratorioResponse> listar(@PathVariable Long pacienteId) {
		return service.listar(pacienteId);
	}

	@PatchMapping("/resultados-laboratorio/{id}/inactivar")
	@PreAuthorize("hasRole('MEDICO')")
	public ResultadoLaboratorioResponse inactivar(@PathVariable Long id,
			@Valid @RequestBody InactivacionRequest req) {
		return service.inactivar(id, req.motivo());
	}

}
