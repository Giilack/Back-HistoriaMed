package com.historiamed.backend.auditoria;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.historiamed.backend.auditoria.dto.RegistroAuditoriaResponse;
import com.historiamed.backend.common.dto.PaginaResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auditoria")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditoriaController {

	private final AuditoriaService service;

	/**
	 * Consulta la auditoría con filtros opcionales. Fechas en ISO-8601, por ejemplo 2026-09-27T00:00:00Z.
	 */
	@GetMapping
	public PaginaResponse<RegistroAuditoriaResponse> buscar(@RequestParam(required = false) String username,
			@RequestParam(required = false) AccionAuditoria accion,
			@RequestParam(required = false) Long pacienteId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
			@PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
		return PaginaResponse.de(service.buscar(username, accion, pacienteId, desde, hasta, pageable),
				RegistroAuditoriaResponse::de);
	}

}
