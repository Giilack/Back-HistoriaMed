package com.historiamed.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.config.HistoriaMedProperties;
import com.historiamed.backend.usuario.Usuario;

import lombok.RequiredArgsConstructor;

/**
 * Refresh tokens opacos con rotación: cada uso entrega uno nuevo y revoca el anterior. Si se presenta un token ya
 * revocado (posible robo), se cierran todas las sesiones del usuario.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

	private final RefreshTokenRepository repository;

	private final AuditoriaService auditoria;

	private final HistoriaMedProperties properties;

	private final SecureRandom random = new SecureRandom();

	/** Resultado de una rotación: el usuario dueño y el nuevo token en claro. */
	public record Rotacion(Usuario usuario, String nuevoToken) {
	}

	/**
	 * Crea un refresh token para el usuario y devuelve el valor en claro (solo se guarda su hash).
	 */
	@Transactional
	public String crear(Usuario usuario) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		repository.save(new RefreshToken(usuario, hash(token), Instant.now().plus(duracion())));
		return token;
	}

	@Transactional(noRollbackFor = CredencialesInvalidasException.class)
	public Rotacion rotar(String token) {
		RefreshToken actual = buscarValido(token);
		actual.setRevocado(true);
		Usuario usuario = actual.getUsuario();
		return new Rotacion(usuario, crear(usuario));
	}

	/**
	 * Revoca el token indicado si existe (logout). Devuelve el usuario dueño, o null si el token no existía.
	 */
	@Transactional
	public Usuario revocar(String token) {
		return repository.findByTokenHash(hash(token)).map(r -> {
			r.setRevocado(true);
			return r.getUsuario();
		}).orElse(null);
	}

	@Transactional
	public void revocarTodos(Long usuarioId) {
		repository.revocarTodosDeUsuario(usuarioId);
	}

	public Duration duracion() {
		return Duration.ofDays(properties.jwt().refreshTokenDias());
	}

	private RefreshToken buscarValido(String token) {
		if (token == null || token.isBlank()) {
			throw new CredencialesInvalidasException("Sesión no encontrada");
		}
		RefreshToken r = repository.findByTokenHash(hash(token))
			.orElseThrow(() -> new CredencialesInvalidasException("Sesión inválida"));
		Usuario u = r.getUsuario();
		if (r.isRevocado()) {
			repository.revocarTodosDeUsuario(u.getId());
			auditoria.registrarComo(u.getId(), u.getUsername(), u.getRol().name(),
					AccionAuditoria.REFRESH_REUTILIZADO, "SESION", r.getId(),
					"Se reutilizó un token revocado; se cerraron todas las sesiones");
			throw new CredencialesInvalidasException("Sesión inválida");
		}
		if (r.getExpiraEn().isBefore(Instant.now())) {
			throw new CredencialesInvalidasException("La sesión expiró");
		}
		if (!u.isActivo()) {
			throw new CredencialesInvalidasException("Usuario desactivado");
		}
		return r;
	}

	static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
