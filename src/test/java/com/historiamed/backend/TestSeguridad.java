package com.historiamed.backend;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.historiamed.backend.config.HistoriaMedProperties;

/**
 * Utilidades para pruebas unitarias: simular un usuario autenticado y propiedades de configuración.
 */
public final class TestSeguridad {

	private TestSeguridad() {
	}

	public static void autenticarComo(Long id, String username, String rol) {
		Jwt jwt = Jwt.withTokenValue("token-de-prueba")
			.header("alg", "HS256")
			.subject(username)
			.claim("uid", id)
			.claim("rol", rol)
			.build();
		SecurityContextHolder.getContext()
			.setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
	}

	public static void limpiar() {
		SecurityContextHolder.clearContext();
	}

	public static HistoriaMedProperties propiedades() {
		return new HistoriaMedProperties(
				new HistoriaMedProperties.Jwt("secreto-de-prueba-con-mas-de-32-caracteres", 15, 7),
				new HistoriaMedProperties.Seguridad(5, 15, false),
				new HistoriaMedProperties.Cors(List.of("http://localhost:5173")),
				new HistoriaMedProperties.AdminInicial("", ""),
				new HistoriaMedProperties.Almacenamiento("uploads"));
	}

}
