package com.historiamed.backend.usuario;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.auth.RefreshTokenService;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.security.UsuarioActual;
import com.historiamed.backend.usuario.dto.PasswordTemporalResponse;
import com.historiamed.backend.usuario.dto.UsuarioActualizarRequest;
import com.historiamed.backend.usuario.dto.UsuarioCrearRequest;
import com.historiamed.backend.usuario.dto.UsuarioResponse;

import lombok.RequiredArgsConstructor;

/**
 * Gestión de usuarios (solo ADMIN). Reglas en plan.md, sección 5.8.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

	private static final String RECURSO = "USUARIO";

	private final UsuarioRepository repository;

	private final PasswordEncoder passwordEncoder;

	private final GeneradorPassword generadorPassword;

	private final RefreshTokenService refreshTokenService;

	private final AuditoriaService auditoria;

	@Transactional(readOnly = true)
	public Page<Usuario> buscar(String texto, Rol rol, Boolean activo, Pageable pageable) {
		Specification<Usuario> spec = Specification.unrestricted();
		if (texto != null && !texto.isBlank()) {
			String patron = "%" + texto.trim().toLowerCase() + "%";
			spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("username")), patron),
					cb.like(cb.lower(root.get("nombres")), patron), cb.like(cb.lower(root.get("apellidos")), patron),
					cb.like(root.get("dni"), patron)));
		}
		if (rol != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("rol"), rol));
		}
		if (activo != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("activo"), activo));
		}
		return repository.findAll(spec, pageable);
	}

	@Transactional(readOnly = true)
	public Usuario obtener(Long id) {
		return repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
	}

	@Transactional(readOnly = true)
	public List<Usuario> medicosActivos() {
		return repository.findByRolAndActivoTrueOrderByApellidosAscNombresAsc(Rol.MEDICO);
	}

	/** Para asignar a una cita: debe ser un MEDICO activo. */
	@Transactional(readOnly = true)
	public Usuario obtenerMedicoActivo(Long id) {
		Usuario u = obtener(id);
		if (u.getRol() != Rol.MEDICO || !u.isActivo()) {
			throw new ReglaNegocioException("El usuario indicado no es un médico activo");
		}
		return u;
	}

	@Transactional
	public PasswordTemporalResponse crear(UsuarioCrearRequest req) {
		if (repository.existsByUsername(req.username())) {
			throw new ReglaNegocioException("El username ya está en uso");
		}
		if (repository.existsByDni(req.dni())) {
			throw new ReglaNegocioException("Ya existe un usuario con ese DNI");
		}
		String email = normalizarEmail(req.email());
		if (email != null && repository.existsByEmail(email)) {
			throw new ReglaNegocioException("Ya existe un usuario con ese email");
		}
		validarCmp(req.rol(), req.cmp());

		Usuario u = new Usuario();
		u.setUsername(req.username());
		u.setNombres(req.nombres().trim());
		u.setApellidos(req.apellidos().trim());
		u.setDni(req.dni());
		u.setEmail(email);
		u.setRol(req.rol());
		u.setCmp(req.rol() == Rol.MEDICO ? req.cmp() : null);
		String passwordTemporal = generadorPassword.generar();
		u.setPasswordHash(passwordEncoder.encode(passwordTemporal));
		u.setDebeCambiarPassword(true);
		repository.save(u);

		auditoria.registrar(AccionAuditoria.CREAR, RECURSO, u.getId(), "Rol " + u.getRol());
		return new PasswordTemporalResponse(UsuarioResponse.de(u), passwordTemporal);
	}

	@Transactional
	public Usuario actualizar(Long id, UsuarioActualizarRequest req) {
		Usuario u = obtener(id);
		if (repository.existsByDniAndIdNot(req.dni(), id)) {
			throw new ReglaNegocioException("Ya existe un usuario con ese DNI");
		}
		String email = normalizarEmail(req.email());
		if (email != null && repository.existsByEmailAndIdNot(email, id)) {
			throw new ReglaNegocioException("Ya existe un usuario con ese email");
		}
		validarCmp(req.rol(), req.cmp());
		if (u.getRol() == Rol.ADMIN && req.rol() != Rol.ADMIN) {
			validarNoEsUltimoAdmin(u);
			if (esUsuarioActual(u)) {
				throw new ReglaNegocioException("No puede quitarse a sí mismo el rol ADMIN");
			}
		}

		Rol rolAnterior = u.getRol();
		u.setNombres(req.nombres().trim());
		u.setApellidos(req.apellidos().trim());
		u.setDni(req.dni());
		u.setEmail(email);
		u.setRol(req.rol());
		u.setCmp(req.rol() == Rol.MEDICO ? req.cmp() : null);

		String detalle = rolAnterior == u.getRol() ? null : "Rol " + rolAnterior + " -> " + u.getRol();
		if (detalle != null) {
			// Un cambio de rol invalida las sesiones: los permisos nuevos se aplican en el próximo ingreso
			refreshTokenService.revocarTodos(u.getId());
		}
		auditoria.registrar(AccionAuditoria.EDITAR, RECURSO, u.getId(), detalle);
		return u;
	}

	@Transactional
	public Usuario desactivar(Long id) {
		Usuario u = obtener(id);
		if (esUsuarioActual(u)) {
			throw new ReglaNegocioException("No puede desactivarse a sí mismo");
		}
		if (u.getRol() == Rol.ADMIN) {
			validarNoEsUltimoAdmin(u);
		}
		u.setActivo(false);
		refreshTokenService.revocarTodos(u.getId());
		auditoria.registrar(AccionAuditoria.DESACTIVAR, RECURSO, u.getId(), null);
		return u;
	}

	@Transactional
	public Usuario activar(Long id) {
		Usuario u = obtener(id);
		u.setActivo(true);
		u.setIntentosFallidos(0);
		u.setBloqueadoHasta(null);
		auditoria.registrar(AccionAuditoria.ACTIVAR, RECURSO, u.getId(), null);
		return u;
	}

	/**
	 * Genera una nueva contraseña temporal, desbloquea la cuenta y cierra sus sesiones abiertas.
	 */
	@Transactional
	public PasswordTemporalResponse resetearPassword(Long id) {
		Usuario u = obtener(id);
		String passwordTemporal = generadorPassword.generar();
		u.setPasswordHash(passwordEncoder.encode(passwordTemporal));
		u.setDebeCambiarPassword(true);
		u.setIntentosFallidos(0);
		u.setBloqueadoHasta(null);
		refreshTokenService.revocarTodos(u.getId());
		auditoria.registrar(AccionAuditoria.RESETEAR_PASSWORD, RECURSO, u.getId(), null);
		return new PasswordTemporalResponse(UsuarioResponse.de(u), passwordTemporal);
	}

	private void validarNoEsUltimoAdmin(Usuario u) {
		if (u.isActivo() && repository.countByRolAndActivoTrue(Rol.ADMIN) <= 1) {
			throw new ReglaNegocioException("Debe existir al menos un ADMIN activo");
		}
	}

	private static void validarCmp(Rol rol, String cmp) {
		if (rol == Rol.MEDICO && (cmp == null || cmp.isBlank())) {
			throw new ReglaNegocioException("El CMP es obligatorio para el rol MEDICO");
		}
	}

	private static boolean esUsuarioActual(Usuario u) {
		return UsuarioActual.obtener().map(actual -> u.getId().equals(actual.id())).orElse(false);
	}

	private static String normalizarEmail(String email) {
		return email == null || email.isBlank() ? null : email.trim().toLowerCase();
	}

}
