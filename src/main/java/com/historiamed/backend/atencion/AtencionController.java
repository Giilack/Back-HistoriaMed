package com.historiamed.backend.atencion;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.historiamed.backend.atencion.dto.AtencionRequest;
import com.historiamed.backend.atencion.dto.AtencionResponse;
import com.historiamed.backend.atencion.dto.ControlPendienteResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Atención médica e historia clínica. Solo MEDICO (plan.md, sección 3: la historia completa es del médico).
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('MEDICO')")
@RequiredArgsConstructor
public class AtencionController {

	private final AtencionService service;

	public record AdendaRequest(@NotBlank @Size(max = 2000) String texto) {
	}

	/** Llamar al paciente: abre (o retoma) la atención de la cita. */
	@PostMapping("/citas/{citaId}/atencion")
	public AtencionResponse iniciar(@PathVariable Long citaId) {
		return service.iniciar(citaId);
	}

	@GetMapping("/citas/{citaId}/atencion")
	public AtencionResponse verPorCita(@PathVariable Long citaId) {
		return service.verPorCita(citaId);
	}

	@GetMapping("/atenciones/{id}")
	public AtencionResponse ver(@PathVariable Long id) {
		return service.ver(id);
	}

	/** Guardar borrador. */
	@PutMapping("/atenciones/{id}")
	public AtencionResponse guardar(@PathVariable Long id, @Valid @RequestBody AtencionRequest req) {
		return service.guardar(id, req);
	}

	/** Guardar y firmar: la atención queda cerrada e inmutable. */
	@PostMapping("/atenciones/{id}/cierre")
	public AtencionResponse cerrar(@PathVariable Long id, @Valid @RequestBody AtencionRequest req) {
		return service.cerrar(id, req);
	}

	@PostMapping("/atenciones/{id}/adendas")
	@ResponseStatus(HttpStatus.CREATED)
	public AtencionResponse agregarAdenda(@PathVariable Long id, @Valid @RequestBody AdendaRequest req) {
		return service.agregarAdenda(id, req.texto());
	}

	/**
	 * Controles que los médicos sugirieron y aún no tienen cita. Lo usa ADMISION para programarlos: no incluye
	 * contenido clínico.
	 */
	@GetMapping("/controles-pendientes")
	@PreAuthorize("hasRole('ADMISION')")
	public List<ControlPendienteResponse> controlesPendientes() {
		return service.controlesPendientes();
	}

	@GetMapping("/pacientes/{pacienteId}/atenciones")
	public List<AtencionResponse> historia(@PathVariable Long pacienteId) {
		return service.historia(pacienteId);
	}

}
