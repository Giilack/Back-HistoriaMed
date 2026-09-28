package com.historiamed.backend.cita;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.historiamed.backend.cita.dto.CancelacionRequest;
import com.historiamed.backend.cita.dto.CitaRequest;
import com.historiamed.backend.cita.dto.CitaResponse;
import com.historiamed.backend.cita.dto.LlegadaSinCitaRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Citas y cola de atención. ADMISION programa y registra llegadas; TRIAJE ve su cola y marca ausencias; el MEDICO
 * ve solo sus citas. El paso por triaje (fase 4) y la consulta (fase 5) se agregan en sus fases.
 */
@RestController
@RequestMapping("/api/citas")
@PreAuthorize("hasAnyRole('ADMISION', 'TRIAJE', 'MEDICO')")
@RequiredArgsConstructor
public class CitaController {

	private final CitaService service;

	/**
	 * Agenda o cola, por fecha (ISO, 2026-09-28) o por paciente. {@code estado} admite varios valores.
	 */
	@GetMapping
	public List<CitaResponse> listar(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
			@RequestParam(required = false) Long pacienteId, @RequestParam(required = false) Long medicoId,
			@RequestParam(required = false) Long consultorioId,
			@RequestParam(name = "estado", required = false) Set<EstadoCita> estados) {
		return service.listar(fecha, pacienteId, medicoId, consultorioId, estados);
	}

	@GetMapping("/{id}")
	public CitaResponse ver(@PathVariable Long id) {
		return service.ver(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMISION')")
	public CitaResponse programar(@Valid @RequestBody CitaRequest req) {
		return service.programar(req);
	}

	@PostMapping("/sin-cita")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('ADMISION')")
	public CitaResponse registrarSinCita(@Valid @RequestBody LlegadaSinCitaRequest req) {
		return service.registrarSinCita(req);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMISION')")
	public CitaResponse reprogramar(@PathVariable Long id, @Valid @RequestBody CitaRequest req) {
		return service.reprogramar(id, req);
	}

	@PatchMapping("/{id}/llegada")
	@PreAuthorize("hasRole('ADMISION')")
	public CitaResponse registrarLlegada(@PathVariable Long id) {
		return service.registrarLlegada(id);
	}

	@PatchMapping("/{id}/cancelar")
	@PreAuthorize("hasRole('ADMISION')")
	public CitaResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelacionRequest req) {
		return service.cancelar(id, req.motivo());
	}

	@PatchMapping("/{id}/no-se-presento")
	@PreAuthorize("hasAnyRole('ADMISION', 'TRIAJE')")
	public CitaResponse marcarNoSePresento(@PathVariable Long id) {
		return service.marcarNoSePresento(id);
	}

}
