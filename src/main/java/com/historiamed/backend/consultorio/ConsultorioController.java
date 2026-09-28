package com.historiamed.backend.consultorio;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.historiamed.backend.consultorio.dto.ConsultorioRequest;
import com.historiamed.backend.consultorio.dto.ConsultorioResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Consultorios: todos los usuarios los consultan (para citas y colas); solo el ADMIN los gestiona.
 */
@RestController
@RequestMapping("/api/consultorios")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ConsultorioController {

	private final ConsultorioService service;

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public List<ConsultorioResponse> listar(@RequestParam(defaultValue = "true") boolean soloActivos) {
		return service.listar(soloActivos).stream().map(ConsultorioResponse::de).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ConsultorioResponse crear(@Valid @RequestBody ConsultorioRequest req) {
		return ConsultorioResponse.de(service.crear(req));
	}

	@PutMapping("/{id}")
	public ConsultorioResponse actualizar(@PathVariable Long id, @Valid @RequestBody ConsultorioRequest req) {
		return ConsultorioResponse.de(service.actualizar(id, req));
	}

	@PatchMapping("/{id}/activar")
	public ConsultorioResponse activar(@PathVariable Long id) {
		return ConsultorioResponse.de(service.cambiarEstado(id, true));
	}

	@PatchMapping("/{id}/desactivar")
	public ConsultorioResponse desactivar(@PathVariable Long id) {
		return ConsultorioResponse.de(service.cambiarEstado(id, false));
	}

}
