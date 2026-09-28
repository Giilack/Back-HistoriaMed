package com.historiamed.backend.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.historiamed.backend.TestSeguridad;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.auth.RefreshTokenService;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.usuario.dto.PasswordTemporalResponse;
import com.historiamed.backend.usuario.dto.UsuarioActualizarRequest;
import com.historiamed.backend.usuario.dto.UsuarioCrearRequest;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

	@Mock
	private UsuarioRepository repository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private RefreshTokenService refreshTokenService;

	@Mock
	private AuditoriaService auditoria;

	private UsuarioService service;

	@BeforeEach
	void setUp() {
		service = new UsuarioService(repository, passwordEncoder, new GeneradorPassword(), refreshTokenService,
				auditoria);
		TestSeguridad.autenticarComo(1L, "admin", "ADMIN");
	}

	@AfterEach
	void tearDown() {
		TestSeguridad.limpiar();
	}

	@Test
	void crearMedicoSinCmpFalla() {
		var req = new UsuarioCrearRequest("jperez", "Juan", "Pérez", "12345678", null, Rol.MEDICO, null);

		assertThatThrownBy(() -> service.crear(req)).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("CMP");
		verify(repository, never()).save(any());
	}

	@Test
	void crearConDniDuplicadoFalla() {
		when(repository.existsByDni("12345678")).thenReturn(true);
		var req = new UsuarioCrearRequest("jperez", "Juan", "Pérez", "12345678", null, Rol.TRIAJE, null);

		assertThatThrownBy(() -> service.crear(req)).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("DNI");
	}

	@Test
	void crearGeneraPasswordTemporalHasheadaYObligaACambiarla() {
		when(passwordEncoder.encode(anyString())).thenReturn("hash");
		var req = new UsuarioCrearRequest("mlopez", "María", "López", "87654321", "M.Lopez@Correo.pe ", Rol.ADMISION,
				"123456");

		PasswordTemporalResponse resp = service.crear(req);

		assertThat(resp.passwordTemporal()).hasSize(12);
		assertThat(resp.usuario().debeCambiarPassword()).isTrue();
		assertThat(resp.usuario().email()).isEqualTo("m.lopez@correo.pe");
		// El CMP solo se guarda para médicos
		assertThat(resp.usuario().cmp()).isNull();
		verify(passwordEncoder).encode(resp.passwordTemporal());
	}

	@Test
	void noPuedeDesactivarseASiMismo() {
		when(repository.findById(1L)).thenReturn(Optional.of(usuario(1L, Rol.ADMIN)));

		assertThatThrownBy(() -> service.desactivar(1L)).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("sí mismo");
	}

	@Test
	void noPuedeDesactivarAlUltimoAdmin() {
		when(repository.findById(2L)).thenReturn(Optional.of(usuario(2L, Rol.ADMIN)));
		when(repository.countByRolAndActivoTrue(Rol.ADMIN)).thenReturn(1L);

		assertThatThrownBy(() -> service.desactivar(2L)).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("al menos un ADMIN");
	}

	@Test
	void desactivarCierraLasSesionesDelUsuario() {
		Usuario medico = usuario(3L, Rol.MEDICO);
		when(repository.findById(3L)).thenReturn(Optional.of(medico));

		service.desactivar(3L);

		assertThat(medico.isActivo()).isFalse();
		verify(refreshTokenService).revocarTodos(3L);
	}

	@Test
	void cambiarRolCierraLasSesiones() {
		Usuario u = usuario(4L, Rol.ADMISION);
		when(repository.findById(4L)).thenReturn(Optional.of(u));
		var req = new UsuarioActualizarRequest("Ana", "Ruiz", "11112222", null, Rol.TRIAJE, null);

		service.actualizar(4L, req);

		assertThat(u.getRol()).isEqualTo(Rol.TRIAJE);
		verify(refreshTokenService).revocarTodos(4L);
	}

	private static Usuario usuario(Long id, Rol rol) {
		Usuario u = new Usuario();
		ReflectionTestUtils.setField(u, "id", id);
		u.setUsername("usuario" + id);
		u.setNombres("Nombre");
		u.setApellidos("Apellido");
		u.setDni("1000000" + id);
		u.setRol(rol);
		u.setCmp(rol == Rol.MEDICO ? "12345" : null);
		return u;
	}

}
