package com.historiamed.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.historiamed.backend.TestSeguridad;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.auth.dto.LoginRequest;
import com.historiamed.backend.usuario.Rol;
import com.historiamed.backend.usuario.Usuario;
import com.historiamed.backend.usuario.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthCaptchaTest {

	@Mock
	private UsuarioRepository usuarioRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private TokenService tokenService;

	@Mock
	private RefreshTokenService refreshTokenService;

	@Mock
	private AuditoriaService auditoria;

	@Mock
	private VerificadorCaptcha captcha;

	private IntentosLogin intentos;

	private AuthService service;

	@BeforeEach
	void setUp() {
		intentos = new IntentosLogin(TestSeguridad.propiedades());
		service = new AuthService(usuarioRepository, passwordEncoder, tokenService, refreshTokenService, auditoria,
				TestSeguridad.propiedades(), intentos, captcha);
		when(captcha.activo()).thenReturn(true);
		when(captcha.intentosSinCaptcha()).thenReturn(3);
		when(tokenService.duracion()).thenReturn(Duration.ofMinutes(30));
	}

	@Test
	void losDosPrimerosFallosDanElMensajeGenerico() {
		when(usuarioRepository.findByUsername("nadie")).thenReturn(Optional.empty());

		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> service.login(new LoginRequest("nadie", "x")))
				.isExactlyInstanceOf(CredencialesInvalidasException.class)
				.hasMessage("Credenciales inválidas");
		}
	}

	@Test
	void elTercerFalloPideElCaptcha() {
		when(usuarioRepository.findByUsername("nadie")).thenReturn(Optional.empty());
		fallar("nadie", 2);

		assertThatThrownBy(() -> service.login(new LoginRequest("nadie", "x")))
			.isInstanceOf(CaptchaRequeridoException.class)
			.hasMessageContaining("Credenciales inválidas");
	}

	@Test
	void usuarioExistenteEInexistenteSeComportanIgual() {
		// Así el CAPTCHA no revela qué usuarios existen
		Usuario u = usuario();
		when(usuarioRepository.findByUsername("medico1")).thenReturn(Optional.of(u));
		when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);
		when(usuarioRepository.findByUsername("nadie")).thenReturn(Optional.empty());
		fallar("medico1", 2);
		fallar("nadie", 2);

		assertThatThrownBy(() -> service.login(new LoginRequest("medico1", "incorrecta")))
			.isInstanceOf(CaptchaRequeridoException.class);
		assertThatThrownBy(() -> service.login(new LoginRequest("nadie", "incorrecta")))
			.isInstanceOf(CaptchaRequeridoException.class);
	}

	@Test
	void sinCaptchaResueltoNiSiquieraSeRevisaLaContrasena() {
		Usuario u = usuario();
		when(usuarioRepository.findByUsername("medico1")).thenReturn(Optional.of(u));
		intentos.registrarFallo("medico1", Instant.now());
		intentos.registrarFallo("medico1", Instant.now());
		intentos.registrarFallo("medico1", Instant.now());
		when(captcha.verificar(null)).thenReturn(false);

		assertThatThrownBy(() -> service.login(new LoginRequest("medico1", "correcta")))
			.isInstanceOf(CaptchaRequeridoException.class)
			.hasMessage("Por seguridad, confirme que no es un robot");
		verify(passwordEncoder, never()).matches("correcta", "hash");
	}

	@Test
	void conCaptchaResueltoYContrasenaCorrectaIngresaYReiniciaElContador() {
		Usuario u = usuario();
		when(usuarioRepository.findByUsername("medico1")).thenReturn(Optional.of(u));
		when(passwordEncoder.matches("correcta", "hash")).thenReturn(true);
		when(refreshTokenService.crear(u)).thenReturn("refresh");
		for (int i = 0; i < 3; i++) {
			intentos.registrarFallo("medico1", Instant.now());
		}
		when(captcha.verificar("token-valido")).thenReturn(true);

		AuthService.Sesion sesion = service.login(new LoginRequest("medico1", "correcta", "token-valido"));

		assertThat(sesion.refreshToken()).isEqualTo("refresh");
		assertThat(intentos.fallos("medico1", Instant.now())).isZero();
	}

	@Test
	void elContadorNoDistingueMayusculasNiEspacios() {
		intentos.registrarFallo(" Medico1 ", Instant.now());
		assertThat(intentos.fallos("medico1", Instant.now())).isEqualTo(1);
	}

	@Test
	void elContadorCaducaConElPlazoDelBloqueo() {
		Instant antes = Instant.now().minus(Duration.ofMinutes(16));
		intentos.registrarFallo("medico1", antes);
		intentos.registrarFallo("medico1", antes);
		intentos.registrarFallo("medico1", antes);

		assertThat(intentos.fallos("medico1", Instant.now())).isZero();
	}

	@Test
	void conElCaptchaDesactivadoNuncaSeExige() {
		when(captcha.activo()).thenReturn(false);
		when(usuarioRepository.findByUsername("nadie")).thenReturn(Optional.empty());
		fallar("nadie", 5);

		assertThatThrownBy(() -> service.login(new LoginRequest("nadie", "x")))
			.isExactlyInstanceOf(CredencialesInvalidasException.class);
		verify(captcha, never()).verificar(any());
		verify(auditoria, never()).registrarComo(any(), anyString(), any(), any(), anyString(), any(),
				org.mockito.ArgumentMatchers.eq("CAPTCHA no resuelto"));
	}

	private void fallar(String username, int veces) {
		for (int i = 0; i < veces; i++) {
			try {
				service.login(new LoginRequest(username, "incorrecta"));
			}
			catch (CredencialesInvalidasException e) {
				// esperado
			}
		}
	}

	private static Usuario usuario() {
		Usuario u = new Usuario();
		ReflectionTestUtils.setField(u, "id", 10L);
		u.setUsername("medico1");
		u.setPasswordHash("hash");
		u.setRol(Rol.MEDICO);
		return u;
	}

}
