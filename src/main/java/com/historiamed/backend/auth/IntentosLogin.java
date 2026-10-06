package com.historiamed.backend.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.historiamed.backend.config.HistoriaMedProperties;

/**
 * Cuenta los intentos fallidos recientes por nombre de usuario, exista o no, para decidir cuándo pedir el CAPTCHA.
 * Contar también los usuarios inexistentes evita que el CAPTCHA delate qué usuarios existen.
 * <p>
 * Vive en memoria: con una sola instancia del backend es suficiente, y tras un reinicio solo se pierde el contador
 * (el bloqueo de la cuenta tras 5 intentos sigue guardado en la base). Cada contador caduca con el mismo plazo del
 * bloqueo.
 */
@Component
public class IntentosLogin {

	/** Límite de usuarios distintos en memoria; al superarlo se descartan los contadores vencidos. */
	private static final int MAXIMO_ENTRADAS = 10_000;

	private record Contador(int fallos, Instant ultimo) {
	}

	private final Map<String, Contador> contadores = new ConcurrentHashMap<>();

	private final Duration vigencia;

	public IntentosLogin(HistoriaMedProperties properties) {
		this.vigencia = Duration.ofMinutes(properties.seguridad().minutosBloqueo());
	}

	/** Intentos fallidos recientes del usuario. */
	public int fallos(String username, Instant ahora) {
		Contador c = contadores.get(clave(username));
		return c == null || vencido(c, ahora) ? 0 : c.fallos();
	}

	/** Suma un intento fallido y devuelve el total reciente. */
	public int registrarFallo(String username, Instant ahora) {
		if (contadores.size() >= MAXIMO_ENTRADAS) {
			contadores.values().removeIf(c -> vencido(c, ahora));
		}
		return contadores
			.merge(clave(username), new Contador(1, ahora),
					(previo, nuevo) -> vencido(previo, ahora) ? nuevo : new Contador(previo.fallos() + 1, ahora))
			.fallos();
	}

	public void limpiar(String username) {
		contadores.remove(clave(username));
	}

	private boolean vencido(Contador c, Instant ahora) {
		return c.ultimo().plus(vigencia).isBefore(ahora);
	}

	private static String clave(String username) {
		return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
	}

}
