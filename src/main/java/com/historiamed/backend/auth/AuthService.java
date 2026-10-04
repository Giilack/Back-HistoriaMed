package com.historiamed.backend.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.auth.dto.CambiarPasswordRequest;
import com.historiamed.backend.auth.dto.LoginRequest;
import com.historiamed.backend.auth.dto.LoginResponse;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.security.UsuarioActual;
import com.historiamed.backend.config.HistoriaMedProperties;
import com.historiamed.backend.usuario.Usuario;
import com.historiamed.backend.usuario.UsuarioRepository;
import com.historiamed.backend.usuario.dto.UsuarioResponse;

/**
 * Login, renovación de sesión, logout y cambio de contraseña. Reglas en plan.md, sección 5.8.
 */
@Service
public class AuthService {

	private static final String RECURSO = "SESION";

	private final UsuarioRepository usuarioRepository;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokenService;

	private final RefreshTokenService refreshTokenService;

	private final AuditoriaService auditoria;

	private final HistoriaMedProperties properties;

	/** Hash de relleno: si el usuario no existe se compara igual, para no revelar su existencia por el tiempo. */
	private final String hashFicticio;

	public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, TokenService tokenService,
			RefreshTokenService refreshTokenService, AuditoriaService auditoria, HistoriaMedProperties properties) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.refreshTokenService = refreshTokenService;
		this.auditoria = auditoria;
		this.properties = properties;
		this.hashFicticio = passwordEncoder.encode("password-ficticia-para-tiempo-constante");
	}

	/** Sesión emitida: la respuesta para el cuerpo y el refresh token para la cookie. */
	public record Sesion(LoginResponse respuesta, String refreshToken) {
	}

	@Transactional(noRollbackFor = CredencialesInvalidasException.class)
	public Sesion login(LoginRequest req) {
		Instant ahora = Instant.now();
		Usuario u = usuarioRepository.findByUsername(req.username().trim()).orElse(null);

		if (u == null) {
			passwordEncoder.matches(req.password(), hashFicticio);
			auditarFallo(null, req.username(), null, AccionAuditoria.LOGIN_FALLIDO, "Credenciales inválidas");
			throw new CredencialesInvalidasException();
		}
		if (!u.isActivo()) {
			auditarFallo(u.getId(), u.getUsername(), u.getRol().name(), AccionAuditoria.LOGIN_FALLIDO,
					"Usuario desactivado");
			throw new CredencialesInvalidasException();
		}
		if (u.estaBloqueado(ahora)) {
			auditarFallo(u.getId(), u.getUsername(), u.getRol().name(), AccionAuditoria.LOGIN_FALLIDO,
					"Intento con cuenta bloqueada");
			throw new CredencialesInvalidasException(
					"Cuenta bloqueada temporalmente por intentos fallidos. Intente más tarde o contacte al administrador");
		}
		if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
			registrarIntentoFallido(u, ahora);
			throw new CredencialesInvalidasException();
		}

		// Hash antiguo (BCrypt): se rehace con Argon2id aprovechando que ahora se conoce la contraseña
		if (passwordEncoder.upgradeEncoding(u.getPasswordHash())) {
			u.setPasswordHash(passwordEncoder.encode(req.password()));
		}
		u.setIntentosFallidos(0);
		u.setBloqueadoHasta(null);
		u.setUltimoAcceso(ahora);
		auditoria.registrarComo(u.getId(), u.getUsername(), u.getRol().name(), AccionAuditoria.LOGIN_EXITOSO, RECURSO,
				null, null);
		return emitirSesion(u);
	}

	@Transactional(noRollbackFor = CredencialesInvalidasException.class)
	public Sesion refrescar(String refreshToken) {
		RefreshTokenService.Rotacion rotacion = refreshTokenService.rotar(refreshToken);
		Usuario u = rotacion.usuario();
		return new Sesion(respuesta(u), rotacion.nuevoToken());
	}

	@Transactional
	public void logout(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return;
		}
		Usuario u = refreshTokenService.revocar(refreshToken);
		if (u != null) {
			auditoria.registrarComo(u.getId(), u.getUsername(), u.getRol().name(), AccionAuditoria.LOGOUT, RECURSO,
					null, null);
		}
	}

	/**
	 * Cambia la contraseña del usuario autenticado, cierra sus otras sesiones y emite una sesión nueva.
	 */
	@Transactional
	public Sesion cambiarPassword(CambiarPasswordRequest req) {
		Usuario u = usuarioAutenticado();
		if (!passwordEncoder.matches(req.passwordActual(), u.getPasswordHash())) {
			throw new ReglaNegocioException("La contraseña actual es incorrecta");
		}
		if (req.passwordActual().equals(req.passwordNueva())) {
			throw new ReglaNegocioException("La nueva contraseña debe ser distinta de la actual");
		}
		u.setPasswordHash(passwordEncoder.encode(req.passwordNueva()));
		u.setDebeCambiarPassword(false);
		refreshTokenService.revocarTodos(u.getId());
		auditoria.registrar(AccionAuditoria.CAMBIO_PASSWORD, "USUARIO", u.getId(), null);
		return emitirSesion(u);
	}

	@Transactional(readOnly = true)
	public UsuarioResponse perfil() {
		return UsuarioResponse.de(usuarioAutenticado());
	}

	private Usuario usuarioAutenticado() {
		Long id = UsuarioActual.requerido().id();
		return usuarioRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
	}

	private void registrarIntentoFallido(Usuario u, Instant ahora) {
		HistoriaMedProperties.Seguridad seguridad = properties.seguridad();
		int intentos = u.getIntentosFallidos() + 1;
		if (intentos >= seguridad.maxIntentosFallidos()) {
			u.setIntentosFallidos(0);
			u.setBloqueadoHasta(ahora.plus(Duration.ofMinutes(seguridad.minutosBloqueo())));
			auditarFallo(u.getId(), u.getUsername(), u.getRol().name(), AccionAuditoria.CUENTA_BLOQUEADA,
					intentos + " intentos fallidos; bloqueada " + seguridad.minutosBloqueo() + " min");
		}
		else {
			u.setIntentosFallidos(intentos);
			auditarFallo(u.getId(), u.getUsername(), u.getRol().name(), AccionAuditoria.LOGIN_FALLIDO,
					"Contraseña incorrecta (intento " + intentos + ")");
		}
	}

	private void auditarFallo(Long id, String username, String rol, AccionAuditoria accion, String detalle) {
		// Se trunca el username porque viene del cliente sin validar su existencia
		String nombre = username == null ? null : username.substring(0, Math.min(username.length(), 30));
		auditoria.registrarComo(id, nombre, rol, accion, RECURSO, null, detalle);
	}

	private Sesion emitirSesion(Usuario u) {
		return new Sesion(respuesta(u), refreshTokenService.crear(u));
	}

	private LoginResponse respuesta(Usuario u) {
		return new LoginResponse(tokenService.generarAccessToken(u), "Bearer", tokenService.duracion().toSeconds(),
				UsuarioResponse.de(u));
	}

}
