package com.historiamed.backend.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades propias del sistema (prefijo {@code historiamed.} en application.properties).
 */
@ConfigurationProperties("historiamed")
public record HistoriaMedProperties(Jwt jwt, Seguridad seguridad, Cors cors, AdminInicial adminInicial,
		Almacenamiento almacenamiento) {

	public record Jwt(String secret, long accessTokenMinutos, long refreshTokenDias) {
	}

	public record Seguridad(int maxIntentosFallidos, long minutosBloqueo, boolean cookieSecure) {
	}

	public record Cors(List<String> origenes) {
	}

	public record AdminInicial(String username, String password) {
	}

	/** @param directorio carpeta del almacenamiento local de documentos */
	public record Almacenamiento(String directorio) {
	}

}
