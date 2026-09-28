package com.historiamed.backend.auditoria;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.historiamed.backend.common.security.UsuarioActual;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

	private final AuditoriaRepository repository;

	/**
	 * Registra una acción del usuario autenticado en la petición actual. Se guarda dentro de la transacción de quien
	 * llama: si la operación se revierte, su registro también (no se audita algo que no ocurrió).
	 */
	public void registrar(AccionAuditoria accion, String recurso, Object recursoId, String detalle) {
		guardar(accion, recurso, recursoId, null, detalle);
	}

	/**
	 * Registra una acción del usuario actual sobre datos de un paciente (misma transacción que {@link #registrar}).
	 * Permite responder "¿quién vio o modificó a este paciente?".
	 */
	public void registrarSobrePaciente(AccionAuditoria accion, String recurso, Object recursoId, Long pacienteId,
			String detalle) {
		guardar(accion, recurso, recursoId, pacienteId, detalle);
	}

	private void guardar(AccionAuditoria accion, String recurso, Object recursoId, Long pacienteId, String detalle) {
		UsuarioActual actor = UsuarioActual.obtener().orElse(new UsuarioActual(null, null, null));
		repository.save(new RegistroAuditoria(actor.id(), actor.username(), actor.rol(), accion, recurso,
				recursoId == null ? null : recursoId.toString(), pacienteId, detalle, ipActual()));
	}

	/**
	 * Registra una acción indicando explícitamente quién la hizo (por ejemplo, en el login, cuando aún no hay
	 * usuario autenticado). Usa su propia transacción: el registro se guarda aunque la operación principal falle.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void registrarComo(Long usuarioId, String username, String rol, AccionAuditoria accion, String recurso,
			Object recursoId, String detalle) {
		repository.save(new RegistroAuditoria(usuarioId, username, rol, accion, recurso,
				recursoId == null ? null : recursoId.toString(), null, detalle, ipActual()));
	}

	@Transactional(readOnly = true)
	public Page<RegistroAuditoria> buscar(String username, AccionAuditoria accion, Long pacienteId, Instant desde,
			Instant hasta, Pageable pageable) {
		Specification<RegistroAuditoria> spec = Specification.unrestricted();
		if (username != null && !username.isBlank()) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("username"), username));
		}
		if (pacienteId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("pacienteId"), pacienteId));
		}
		if (accion != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("accion"), accion));
		}
		if (desde != null) {
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fecha"), desde));
		}
		if (hasta != null) {
			spec = spec.and((root, query, cb) -> cb.lessThan(root.get("fecha"), hasta));
		}
		return repository.findAll(spec, pageable);
	}

	private static String ipActual() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
			return attrs.getRequest().getRemoteAddr();
		}
		return null;
	}

}
