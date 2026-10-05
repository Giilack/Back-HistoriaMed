package com.historiamed.backend.ia;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Si la IA está configurada: el frontend muestra el botón "Extraer con IA" solo cuando lo está. */
@RestController
@RequestMapping("/api/ia")
@PreAuthorize("hasAnyRole('TRIAJE', 'MEDICO')")
@RequiredArgsConstructor
public class IaController {

	private final ProveedorIA proveedor;

	public record EstadoIa(boolean disponible, String modelo) {
	}

	@GetMapping("/estado")
	public EstadoIa estado() {
		return new EstadoIa(proveedor.disponible(), proveedor.disponible() ? proveedor.modelo() : null);
	}

}
