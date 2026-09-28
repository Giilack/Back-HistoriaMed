package com.historiamed.backend.alergia;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.historiamed.backend.alergia.dto.AlergiaRequest;
import com.historiamed.backend.alergia.dto.AlergiaResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Alergias del paciente. Las registran y consultan TRIAJE y MEDICO (dato clínico: ADMISION y ADMIN no acceden).
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('TRIAJE', 'MEDICO')")
@RequiredArgsConstructor
public class AlergiaController {

	private final AlergiaService service;

	public record InactivacionRequest(@NotBlank @Size(max = 200) String motivo) {
	}

	@GetMapping("/pacientes/{pacienteId}/alergias")
	public List<AlergiaResponse> listar(@PathVariable Long pacienteId) {
		return service.listar(pacienteId);
	}

	@PostMapping("/pacientes/{pacienteId}/alergias")
	@ResponseStatus(HttpStatus.CREATED)
	public AlergiaResponse registrar(@PathVariable Long pacienteId, @Valid @RequestBody AlergiaRequest req) {
		return service.registrar(pacienteId, req);
	}

	@PatchMapping("/alergias/{id}/inactivar")
	public AlergiaResponse inactivar(@PathVariable Long id, @Valid @RequestBody InactivacionRequest req) {
		return service.inactivar(id, req.motivo());
	}

}
