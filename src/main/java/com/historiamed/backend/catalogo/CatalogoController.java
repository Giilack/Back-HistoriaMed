package com.historiamed.backend.catalogo;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Búsqueda en los catálogos de diagnósticos y medicamentos. Son datos de referencia, no de pacientes: los usan el
 * MEDICO al atender y TRIAJE o MEDICO al llenar la revisión de un documento.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('TRIAJE', 'MEDICO')")
@RequiredArgsConstructor
public class CatalogoController {

	private final CatalogoService service;

	public record Cie10Response(String codigo, String descripcion) {
	}

	public record MedicamentoResponse(Long id, String nombre, String concentracion, String formaFarmaceutica,
			String descripcion) {
	}

	@GetMapping("/cie10")
	public List<Cie10Response> buscarCie10(@RequestParam(defaultValue = "") String q) {
		return service.buscarCie10(q).stream().map(c -> new Cie10Response(c.getCodigo(), c.getDescripcion())).toList();
	}

	@GetMapping("/medicamentos")
	public List<MedicamentoResponse> buscarMedicamentos(@RequestParam(defaultValue = "") String q) {
		return service.buscarMedicamentos(q)
			.stream()
			.map(m -> new MedicamentoResponse(m.getId(), m.getNombre(), m.getConcentracion(), m.getFormaFarmaceutica(),
					m.descripcion()))
			.toList();
	}

}
