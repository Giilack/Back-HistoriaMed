package com.historiamed.backend.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.historiamed.backend.auth.dto.CambiarPasswordRequest;
import com.historiamed.backend.auth.dto.LoginRequest;
import com.historiamed.backend.auth.dto.LoginResponse;
import com.historiamed.backend.config.HistoriaMedProperties;
import com.historiamed.backend.usuario.dto.UsuarioResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Autenticación. El access token va en el cuerpo (el frontend lo guarda en memoria) y el refresh token en una
 * cookie httpOnly limitada a /api/auth, inaccesible desde JavaScript.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	static final String COOKIE_REFRESH = "hm_refresh";

	private static final String RUTA_COOKIE = "/api/auth";

	private final AuthService service;

	private final RefreshTokenService refreshTokenService;

	private final HistoriaMedProperties properties;

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
		return conCookie(service.login(req));
	}

	@PostMapping("/refresh")
	public ResponseEntity<LoginResponse> refrescar(
			@CookieValue(name = COOKIE_REFRESH, required = false) String refreshToken) {
		return conCookie(service.refrescar(refreshToken));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = COOKIE_REFRESH, required = false) String refreshToken) {
		service.logout(refreshToken);
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
	}

	@PostMapping("/cambiar-password")
	public ResponseEntity<LoginResponse> cambiarPassword(@Valid @RequestBody CambiarPasswordRequest req) {
		return conCookie(service.cambiarPassword(req));
	}

	@GetMapping("/me")
	public UsuarioResponse perfil() {
		return service.perfil();
	}

	private ResponseEntity<LoginResponse> conCookie(AuthService.Sesion sesion) {
		ResponseCookie cookie = cookie(sesion.refreshToken(), refreshTokenService.duracion());
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(sesion.respuesta());
	}

	private ResponseCookie cookie(String valor, Duration maxAge) {
		return ResponseCookie.from(COOKIE_REFRESH, valor)
			.httpOnly(true)
			.secure(properties.seguridad().cookieSecure())
			.sameSite("Strict")
			.path(RUTA_COOKIE)
			.maxAge(maxAge)
			.build();
	}

}
