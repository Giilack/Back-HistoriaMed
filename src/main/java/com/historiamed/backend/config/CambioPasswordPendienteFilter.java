package com.historiamed.backend.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.historiamed.backend.common.security.EscritorErrorJson;
import com.historiamed.backend.common.security.UsuarioActual;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Mientras el usuario tenga una contraseña temporal, solo puede usar /api/auth/** (cambiarla, ver su perfil o salir).
 * No es un @Component a propósito: se registra solo dentro de la cadena de seguridad.
 */
class CambioPasswordPendienteFilter extends OncePerRequestFilter {

	private final EscritorErrorJson escritorError;

	CambioPasswordPendienteFilter(EscritorErrorJson escritorError) {
		this.escritorError = escritorError;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof JwtAuthenticationToken token
				&& Boolean.TRUE.equals(token.getToken().getClaimAsBoolean(UsuarioActual.CLAIM_CAMBIO_PENDIENTE))
				&& !request.getRequestURI().startsWith("/api/auth/")) {
			escritorError.escribir(request, response, HttpStatus.FORBIDDEN.value(), "CAMBIO_PASSWORD_REQUERIDO",
					"Debe cambiar su contraseña temporal antes de continuar");
			return;
		}
		chain.doFilter(request, response);
	}

}
