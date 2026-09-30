package com.historiamed.backend.config;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.historiamed.backend.common.security.EscritorErrorJson;
import com.historiamed.backend.common.security.UsuarioActual;

/**
 * API sin estado (stateless) protegida con JWT. Los permisos por rol se declaran en cada controlador con
 * {@code @PreAuthorize}; aquí solo se definen las rutas públicas.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, EscritorErrorJson escritorError) throws Exception {
		http
			// Sin sesiones ni formularios: el token viaja en el header Authorization. La cookie de refresh es
			// SameSite=Strict, lo que la protege de CSRF.
			.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
				.permitAll()
				.requestMatchers("/actuator/health", "/error")
				.permitAll()
				.anyRequest()
				.authenticated())
			.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorRoles()))
				.authenticationEntryPoint((req, res, ex) -> escritorError.escribir(req, res,
						HttpStatus.UNAUTHORIZED.value(), "NO_AUTENTICADO", "Token ausente, inválido o expirado"))
				.accessDeniedHandler((req, res, ex) -> escritorError.escribir(req, res, HttpStatus.FORBIDDEN.value(),
						"ACCESO_DENEGADO", "No tiene permisos para esta operación")))
			.addFilterAfter(new CambioPasswordPendienteFilter(escritorError), BearerTokenAuthenticationFilter.class);
		return http.build();
	}

	/**
	 * Convierte el claim "rol" del JWT en la autoridad ROLE_xxx que usa {@code hasRole('xxx')}.
	 */
	private static JwtAuthenticationConverter convertidorRoles() {
		JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
		roles.setAuthoritiesClaimName(UsuarioActual.CLAIM_ROL);
		roles.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(roles);
		return converter;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return crearPasswordEncoder();
	}

	/**
	 * Contraseñas con hash Argon2id (recomendación de OWASP: 19 MiB de memoria, 2 iteraciones, 1 hilo). Los hashes
	 * se guardan con prefijo ({@code {argon2}...}). Los hashes BCrypt anteriores, sin prefijo, siguen validándose y
	 * se rehacen con Argon2id cuando el usuario inicia sesión (ver {@code AuthService.login}).
	 */
	static PasswordEncoder crearPasswordEncoder() {
		PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
		PasswordEncoder bcrypt = new BCryptPasswordEncoder();
		DelegatingPasswordEncoder codificador = new DelegatingPasswordEncoder(ID_ARGON2,
				Map.of(ID_ARGON2, argon2, "bcrypt", bcrypt));
		codificador.setDefaultPasswordEncoderForMatches(bcrypt);
		return codificador;
	}

	private static final String ID_ARGON2 = "argon2";

	@Bean
	CorsConfigurationSource corsConfigurationSource(HistoriaMedProperties properties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(properties.cors().origenes());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		// Necesario para que el navegador envíe la cookie de refresh
		config.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

}
