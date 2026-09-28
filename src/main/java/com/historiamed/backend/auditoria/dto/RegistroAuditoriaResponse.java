package com.historiamed.backend.auditoria.dto;

import java.time.Instant;

import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.RegistroAuditoria;

public record RegistroAuditoriaResponse(Long id, Instant fecha, Long usuarioId, String username, String rol,
		AccionAuditoria accion, String recurso, String recursoId, Long pacienteId, String detalle, String ip) {

	public static RegistroAuditoriaResponse de(RegistroAuditoria r) {
		return new RegistroAuditoriaResponse(r.getId(), r.getFecha(), r.getUsuarioId(), r.getUsername(), r.getRol(),
				r.getAccion(), r.getRecurso(), r.getRecursoId(), r.getPacienteId(), r.getDetalle(), r.getIp());
	}

}
