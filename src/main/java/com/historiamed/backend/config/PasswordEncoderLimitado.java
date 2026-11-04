package com.historiamed.backend.config;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Limita cuántas contraseñas se calculan a la vez. Cada hash Argon2id reserva 19 MiB: con muchos inicios de sesión
 * simultáneos la instancia gratuita (512 MB) se queda sin memoria y se reinicia. Con este tope, los demás esperan
 * su turno unos milisegundos en lugar de tumbar el servicio.
 */
final class PasswordEncoderLimitado implements PasswordEncoder {

	private final PasswordEncoder delegado;
	private final Semaphore permisos;

	PasswordEncoderLimitado(PasswordEncoder delegado, int simultaneos) {
		this.delegado = delegado;
		this.permisos = new Semaphore(simultaneos, true);
	}

	@Override
	public String encode(CharSequence rawPassword) {
		return conPermiso(() -> delegado.encode(rawPassword));
	}

	@Override
	public boolean matches(CharSequence rawPassword, String encodedPassword) {
		return conPermiso(() -> delegado.matches(rawPassword, encodedPassword));
	}

	@Override
	public boolean upgradeEncoding(String encodedPassword) {
		return delegado.upgradeEncoding(encodedPassword);
	}

	private <T> T conPermiso(Supplier<T> calculo) {
		try {
			permisos.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Cálculo de contraseña interrumpido", e);
		}
		try {
			return calculo.get();
		} finally {
			permisos.release();
		}
	}
}
