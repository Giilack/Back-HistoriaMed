package com.historiamed.backend.common.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Datos del usuario autenticado, leídos del JWT de la petición actual.
 */
public record UsuarioActual(Long id, String username, String rol) {

	public static final String CLAIM_ID = "uid";

	public static final String CLAIM_ROL = "rol";

	public static final String CLAIM_CAMBIO_PENDIENTE = "cambio_pendiente";

	public static Optional<UsuarioActual> obtener() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof JwtAuthenticationToken token) {
			return Optional.of(desdeJwt(token.getToken()));
		}
		return Optional.empty();
	}

	public static UsuarioActual requerido() {
		return obtener().orElseThrow(() -> new IllegalStateException("No hay un usuario autenticado"));
	}

	private static UsuarioActual desdeJwt(Jwt jwt) {
		Object id = jwt.getClaim(CLAIM_ID);
		return new UsuarioActual(id instanceof Number n ? n.longValue() : null, jwt.getSubject(),
				jwt.getClaimAsString(CLAIM_ROL));
	}

}
