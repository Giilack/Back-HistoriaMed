package com.historiamed.backend.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifrado de datos personales sensibles de los pacientes (documento, teléfonos, dirección) con AES-256-GCM.
 * <p>
 * A diferencia de las contraseñas (hash, irreversible), estos datos deben poder leerse, por eso se cifran. Cada
 * valor lleva su propio vector de inicialización aleatorio, así que dos valores iguales producen textos cifrados
 * distintos. Para buscar o verificar duplicados sin descifrar se usa una <b>huella</b> (HMAC-SHA256) del valor.
 * <p>
 * Formato guardado: {@code v1:} + Base64(IV de 12 bytes + texto cifrado + etiqueta de 16 bytes). El prefijo
 * permite cambiar de algoritmo en el futuro y distinguir valores ya cifrados.
 * <p>
 * La clave maestra (32 bytes en Base64, variable {@code CIFRADO_CLAVE}) no se guarda en la base de datos ni en
 * Git. <b>Si se pierde, los datos cifrados no se pueden recuperar.</b>
 */
@Component
public class CifradoDatos {

	public static final String PREFIJO = "v1:";

	private static final String ALGORITMO = "AES/GCM/NoPadding";

	private static final int BYTES_IV = 12;

	private static final int BITS_ETIQUETA = 128;

	private final SecretKey claveCifrado;

	private final SecretKey claveHuella;

	private final SecureRandom azar = new SecureRandom();

	public CifradoDatos(@Value("${historiamed.cifrado.clave:}") String claveMaestraBase64) {
		byte[] maestra = decodificarClave(claveMaestraBase64);
		// Dos claves distintas derivadas de la maestra: una para cifrar y otra para las huellas
		this.claveCifrado = new SecretKeySpec(derivar(maestra, "historiamed-cifrado-v1"), "AES");
		this.claveHuella = new SecretKeySpec(derivar(maestra, "historiamed-huella-v1"), "HmacSHA256");
	}

	/** Cifra el texto; {@code null} queda {@code null}. */
	public String cifrar(String texto) {
		if (texto == null) {
			return null;
		}
		try {
			byte[] iv = new byte[BYTES_IV];
			azar.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(ALGORITMO);
			cipher.init(Cipher.ENCRYPT_MODE, claveCifrado, new GCMParameterSpec(BITS_ETIQUETA, iv));
			byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
			byte[] salida = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
			return PREFIJO + Base64.getEncoder().encodeToString(salida);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo cifrar el dato", e);
		}
	}

	/**
	 * Descifra un valor producido por {@link #cifrar}; {@code null} queda {@code null}. Falla si el valor fue
	 * alterado o se cifró con otra clave (GCM verifica la integridad).
	 */
	public String descifrar(String valor) {
		if (valor == null) {
			return null;
		}
		if (!estaCifrado(valor)) {
			throw new IllegalStateException("El dato no está cifrado con el formato esperado");
		}
		try {
			byte[] datos = Base64.getDecoder().decode(valor.substring(PREFIJO.length()));
			Cipher cipher = Cipher.getInstance(ALGORITMO);
			cipher.init(Cipher.DECRYPT_MODE, claveCifrado, new GCMParameterSpec(BITS_ETIQUETA, datos, 0, BYTES_IV));
			byte[] texto = cipher.doFinal(datos, BYTES_IV, datos.length - BYTES_IV);
			return new String(texto, StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("No se pudo descifrar el dato (¿clave CIFRADO_CLAVE distinta?)", e);
		}
	}

	public static boolean estaCifrado(String valor) {
		return valor != null && valor.startsWith(PREFIJO);
	}

	/**
	 * Huella determinista del valor (HMAC-SHA256 en hexadecimal, 64 caracteres): el mismo valor siempre da la misma
	 * huella, pero sin la clave no se puede calcular ni revertir. Sirve para buscar y para el índice único.
	 */
	public String huella(String texto) {
		if (texto == null) {
			return null;
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(claveHuella);
			return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo calcular la huella", e);
		}
	}

	private static byte[] decodificarClave(String base64) {
		if (base64 == null || base64.isBlank()) {
			throw new IllegalStateException(
					"Falta CIFRADO_CLAVE en backend/.env (32 bytes en Base64; ver .env.example para generarla)");
		}
		byte[] clave;
		try {
			clave = Base64.getDecoder().decode(base64.trim());
		}
		catch (IllegalArgumentException e) {
			throw new IllegalStateException("CIFRADO_CLAVE no es Base64 válido", e);
		}
		if (clave.length != 32) {
			throw new IllegalStateException("CIFRADO_CLAVE debe tener 32 bytes (tiene " + clave.length + ")");
		}
		return clave;
	}

	/** Deriva una subclave de 32 bytes: HMAC-SHA256(maestra, propósito). */
	private static byte[] derivar(byte[] maestra, String proposito) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(maestra, "HmacSHA256"));
			return mac.doFinal(proposito.getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo derivar la clave", e);
		}
	}

}
