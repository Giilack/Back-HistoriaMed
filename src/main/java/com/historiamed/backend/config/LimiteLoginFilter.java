package com.historiamed.backend.config;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.historiamed.backend.common.security.EscritorErrorJson;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Deja pasar pocos inicios de sesión a la vez. Cada uno calcula un hash Argon2id (19 MiB y CPU) y mantiene abierta
 * una conexión a la base mientras dura: sin este tope, un pico de inicios de sesión agota la memoria de la instancia
 * gratuita (512 MB) o deja sin conexiones al resto del sistema. El exceso espera unos segundos su turno y, si no lo
 * consigue, recibe 503 para que lo intente de nuevo, en lugar de formar una cola de minutos.
 * No es un @Component a propósito: se registra solo dentro de la cadena de seguridad.
 */
class LimiteLoginFilter extends OncePerRequestFilter {

	static final String RUTA = "/api/auth/login";

	private final EscritorErrorJson escritorError;
	private final Semaphore permisos;
	private final long esperaMaximaMs;

	LimiteLoginFilter(EscritorErrorJson escritorError, int simultaneos, long esperaMaximaMs) {
		this.escritorError = escritorError;
		this.permisos = new Semaphore(simultaneos, true);
		this.esperaMaximaMs = esperaMaximaMs;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !("POST".equals(request.getMethod()) && RUTA.equals(request.getRequestURI()));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		boolean conPermiso;
		try {
			conPermiso = permisos.tryAcquire(esperaMaximaMs, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			conPermiso = false;
		}
		if (!conPermiso) {
			response.setHeader("Retry-After", "5");
			escritorError.escribir(request, response, HttpStatus.SERVICE_UNAVAILABLE.value(), "SERVICIO_OCUPADO",
					"Hay muchos inicios de sesión a la vez. Intente de nuevo en unos segundos");
			return;
		}
		try {
			chain.doFilter(request, response);
		} finally {
			permisos.release();
		}
	}
}
