package com.historiamed.backend.usuario;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Genera contraseñas temporales aleatorias (sin caracteres ambiguos como 0/O o 1/l).
 */
@Component
public class GeneradorPassword {

	private static final String LETRAS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";

	private static final String DIGITOS = "23456789";

	private static final String TODOS = LETRAS + DIGITOS;

	private static final int LONGITUD = 12;

	private final SecureRandom random = new SecureRandom();

	public String generar() {
		char[] password = new char[LONGITUD];
		// Garantiza al menos una letra y un dígito (política de contraseñas)
		password[0] = LETRAS.charAt(random.nextInt(LETRAS.length()));
		password[1] = DIGITOS.charAt(random.nextInt(DIGITOS.length()));
		for (int i = 2; i < LONGITUD; i++) {
			password[i] = TODOS.charAt(random.nextInt(TODOS.length()));
		}
		// Mezcla para que la letra y el dígito no queden siempre al inicio
		for (int i = LONGITUD - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			char tmp = password[i];
			password[i] = password[j];
			password[j] = tmp;
		}
		return new String(password);
	}

}
