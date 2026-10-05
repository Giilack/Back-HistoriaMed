package com.historiamed.backend.extraccion;

import java.util.List;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.historiamed.backend.extraccion.dto.ExtraccionResponse;
import com.historiamed.backend.extraccion.dto.ExtraccionResumenResponse;
import com.historiamed.backend.extraccion.dto.ItemRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Revisión de los datos de un documento clínico. La llenan TRIAJE y MEDICO; solo el MEDICO la valida o rechaza.
 * ADMISION sube documentos pero no accede aquí: son datos clínicos (plan.md, principio P1).
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('TRIAJE', 'MEDICO')")
@RequiredArgsConstructor
public class ExtraccionController {

	private final ExtraccionService service;

	/** @param aceptados identificadores de los datos que pasan a la historia; los demás quedan descartados */
	public record ValidacionRequest(@NotNull Set<Long> aceptados) {
	}

	public record RechazoRequest(@NotBlank @Size(max = 300) String motivo) {
	}

	/** Abre la revisión del documento: devuelve la vigente o crea una nueva para llenarla a mano. */
	@PostMapping("/documentos/{documentoId}/extraccion")
	public ExtraccionResponse iniciar(@PathVariable Long documentoId) {
		return service.iniciar(documentoId);
	}

	@GetMapping("/documentos/{documentoId}/extraccion")
	public ExtraccionResponse delDocumento(@PathVariable Long documentoId) {
		return service.delDocumento(documentoId);
	}

	/** Estado de la revisión de cada documento del paciente (sin los datos). */
	@GetMapping("/pacientes/{pacienteId}/extracciones")
	public List<ExtraccionResumenResponse> delPaciente(@PathVariable Long pacienteId) {
		return service.delPaciente(pacienteId);
	}

	@PostMapping("/extracciones/{id}/items")
	public ExtraccionResponse agregarItem(@PathVariable Long id, @Valid @RequestBody ItemRequest req) {
		return service.agregarItem(id, req);
	}

	@PutMapping("/extracciones/{id}/items/{itemId}")
	public ExtraccionResponse editarItem(@PathVariable Long id, @PathVariable Long itemId,
			@Valid @RequestBody ItemRequest req) {
		return service.editarItem(id, itemId, req);
	}

	@DeleteMapping("/extracciones/{id}/items/{itemId}")
	public ExtraccionResponse eliminarItem(@PathVariable Long id, @PathVariable Long itemId) {
		return service.eliminarItem(id, itemId);
	}

	@PostMapping("/extracciones/{id}/validacion")
	@PreAuthorize("hasRole('MEDICO')")
	public ExtraccionResponse validar(@PathVariable Long id, @Valid @RequestBody ValidacionRequest req) {
		return service.validar(id, req.aceptados());
	}

	@PostMapping("/extracciones/{id}/rechazo")
	@PreAuthorize("hasRole('MEDICO')")
	public ExtraccionResponse rechazar(@PathVariable Long id, @Valid @RequestBody RechazoRequest req) {
		return service.rechazar(id, req.motivo());
	}

}
