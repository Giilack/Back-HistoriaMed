package com.historiamed.backend.usuario;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

import com.historiamed.backend.common.dto.PaginaResponse;
import com.historiamed.backend.usuario.dto.PasswordTemporalResponse;
import com.historiamed.backend.usuario.dto.UsuarioActualizarRequest;
import com.historiamed.backend.usuario.dto.UsuarioCrearRequest;
import com.historiamed.backend.usuario.dto.UsuarioResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UsuarioController {

	private final UsuarioService service;

	@GetMapping
	public PaginaResponse<UsuarioResponse> buscar(@RequestParam(required = false) String texto,
			@RequestParam(required = false) Rol rol, @RequestParam(required = false) Boolean activo,
			@PageableDefault(size = 20, sort = "apellidos", direction = Sort.Direction.ASC) Pageable pageable) {
		return PaginaResponse.de(service.buscar(texto, rol, activo, pageable), UsuarioResponse::de);
	}

	@GetMapping("/{id}")
	public UsuarioResponse obtener(@PathVariable Long id) {
		return UsuarioResponse.de(service.obtener(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PasswordTemporalResponse crear(@Valid @RequestBody UsuarioCrearRequest req) {
		return service.crear(req);
	}

	@PutMapping("/{id}")
	public UsuarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioActualizarRequest req) {
		return UsuarioResponse.de(service.actualizar(id, req));
	}

	@PatchMapping("/{id}/desactivar")
	public UsuarioResponse desactivar(@PathVariable Long id) {
		return UsuarioResponse.de(service.desactivar(id));
	}

	@PatchMapping("/{id}/activar")
	public UsuarioResponse activar(@PathVariable Long id) {
		return UsuarioResponse.de(service.activar(id));
	}

	@PostMapping("/{id}/resetear-password")
	public PasswordTemporalResponse resetearPassword(@PathVariable Long id) {
		return service.resetearPassword(id);
	}

}
