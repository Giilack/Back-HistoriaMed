package com.historiamed.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.historiamed.backend.TestSeguridad;
import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.auth.dto.LoginRequest;
import com.historiamed.backend.usuario.Rol;
import com.historiamed.backend.usuario.Usuario;
import com.historiamed.backend.usuario.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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

	private AuthService service;

	@BeforeEach
	void setUp() {
		service = new AuthService(usuarioRepository, passwordEncoder, tokenService, refreshTokenService, auditoria,
				TestSeguridad.propiedades());
	}

	@Test
	void usuarioInexistenteDaElMismoMensajeQueContrasenaIncorrecta() {
		when(usuarioRepository.findByUsername("nadie")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.login(new LoginRequest("nadie", "x")))
			.isInstanceOf(CredencialesInvalidasException.class)
			.hasMessage("Credenciales inválidas");
	}

	@Test
	void usuarioDesactivadoNoPuedeIngresar() {
		Usuario u = usuario();
		u.setActivo(false);
		when(usuarioRepository.findByUsername("medico1")).thenReturn(Optional.of(u));

		assertThatThrownBy(() -> service.login(new LoginRequest("medico1", "correcta")))
			.isInstanceOf(CredencialesInvalidasException.class);
		verify(passwordEncoder, never()).matches("correcta", "hash");
	}

	@Test
	void quintoIntentoFallidoBloqueaLaCuenta() {
		Usuario u = usuario();
		u.setIntentosFallidos(4);
		when(usuarioRepository.findByUsername("medico1")).thenReturn(Optional.of(u));
		when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

		assertThatThrownBy(() -> service.login(new LoginRequest("medico1", "incorrecta")))
			.isInstanceOf(CredencialesInvalidasException.class);

		assertThat(u.estaBloqueado(Instant.now())).isTrue();
		assertThat(u.getIntentosFallidos()).isZero();
		verify(auditoria).registrarComo(eq(10L), eq("medico1"), eq("MEDICO"), eq(AccionAuditoria.CUENTA_BLOQUEADA),
				anyString(), any(), anyString());
	}

	@Test
	void cuentaBloqueadaRechazaAunqueLaContrasenaSeaCorrecta() {
		Usuario u = usuario();
		u.setBloqueadoHasta(Instant.now().plus(Duration.ofMinutes(10)));
		when(usuarioRepository.findByUsername("medico1")).thenReturn(Optional.of(u));

		assertThatThrownBy(() -> service.login(new LoginRequest("medico1", "correcta")))
			.isInstanceOf(CredencialesInvalidasException.class)
			.hasMessageContaining("bloqueada");
	}

	@Test
	void loginExitosoReiniciaIntentosYEmiteSesion() {
		Usuario u = usuario();
		u.setIntentosFallidos(3);
		when(usuarioRepository.findByUsername("medico1")).thenReturn(Optional.of(u));
		when(passwordEncoder.matches("correcta", "hash")).thenReturn(true);
		when(tokenService.generarAccessToken(u)).thenReturn("jwt");
		when(tokenService.duracion()).thenReturn(Duration.ofMinutes(15));
		when(refreshTokenService.crear(u)).thenReturn("refresh");

		AuthService.Sesion sesion = service.login(new LoginRequest("medico1", "correcta"));

		assertThat(sesion.respuesta().accessToken()).isEqualTo("jwt");
		assertThat(sesion.respuesta().expiraEnSegundos()).isEqualTo(900);
		assertThat(sesion.refreshToken()).isEqualTo("refresh");
		assertThat(u.getIntentosFallidos()).isZero();
		assertThat(u.getUltimoAcceso()).isNotNull();
	}

	@Test
	void loginConHashAntiguoLoRehaceConArgon2() {
		Usuario u = usuario();
		when(usuarioRepository.findByUsername("medico1")).thenReturn(Optional.of(u));
		when(passwordEncoder.matches("correcta", "hash")).thenReturn(true);
		when(passwordEncoder.upgradeEncoding("hash")).thenReturn(true);
		when(passwordEncoder.encode("correcta")).thenReturn("{argon2}nuevo");
		when(tokenService.duracion()).thenReturn(Duration.ofMinutes(15));

		service.login(new LoginRequest("medico1", "correcta"));

		assertThat(u.getPasswordHash()).isEqualTo("{argon2}nuevo");
	}

	private static Usuario usuario() {
		Usuario u = new Usuario();
		ReflectionTestUtils.setField(u, "id", 10L);
		u.setUsername("medico1");
		u.setPasswordHash("hash");
		u.setNombres("Carlos");
		u.setApellidos("Díaz");
		u.setDni("44445555");
		u.setRol(Rol.MEDICO);
		u.setCmp("12345");
		return u;
	}

}
