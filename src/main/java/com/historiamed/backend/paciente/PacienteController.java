package com.historiamed.backend.paciente;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.historiamed.backend.common.dto.PaginaResponse;
import com.historiamed.backend.paciente.dto.FinanciamientoRequest;
import com.historiamed.backend.paciente.dto.PacienteRequest;
import com.historiamed.backend.paciente.dto.PacienteResponse;
import com.historiamed.backend.paciente.dto.PacienteResumenResponse;
import com.historiamed.backend.paciente.dto.VerificacionSeguroRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Pacientes. Buscan y consultan ADMISION, TRIAJE y MEDICO; solo ADMISION registra y modifica. El ADMIN no tiene
 * acceso (plan.md, principio P1).
 */
@RestController
@RequestMapping("/api/pacientes")
@PreAuthorize("hasAnyRole('ADMISION', 'TRIAJE', 'MEDICO')")
@RequiredArgsConstructor
public class PacienteController {

	private final PacienteService service;

	/**
	 * @param q número de HC (HC-000012), número de documento o nombres y apellidos
	 */
	@GetMapping
	public PaginaResponse<PacienteResumenResponse> buscar(@RequestParam(required = false) String q,
			@PageableDefault(size = 20, sort = { "apellidoPaterno", "apellidoMaterno",
					"nombres" }, direction = Sort.Direction.ASC) Pageable pageable) {
		return PaginaResponse.de(service.buscar(q, pageable), PacienteResumenResponse::de);
	}

	@GetMapping("/{id}")
	public PacienteResponse ver(@PathVariable Long id) {
		return PacienteResponse.de(service.ver(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMISION')")
	public PacienteResponse registrar(@Valid @RequestBody PacienteRequest req) {
		return PacienteResponse.de(service.registrar(req));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMISION')")
	public PacienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody PacienteRequest req) {
		return PacienteResponse.de(service.actualizar(id, req));
	}

	@PutMapping("/{id}/financiamiento")
	@PreAuthorize("hasRole('ADMISION')")
	public PacienteResponse cambiarFinanciamiento(@PathVariable Long id,
			@Valid @RequestBody FinanciamientoRequest req) {
		return PacienteResponse.de(service.cambiarFinanciamiento(id, req));
	}

	@PostMapping("/{id}/financiamiento/verificacion")
	@PreAuthorize("hasRole('ADMISION')")
	public PacienteResponse verificarSeguro(@PathVariable Long id, @Valid @RequestBody VerificacionSeguroRequest req) {
		return PacienteResponse.de(service.verificarSeguro(id, req));
	}

}
