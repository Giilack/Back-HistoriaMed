package com.historiamed.backend.antecedente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.historiamed.backend.TestSeguridad;
import com.historiamed.backend.antecedente.dto.AntecedenteResponse;
import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.PacienteService;
import com.historiamed.backend.usuario.Usuario;
import com.historiamed.backend.usuario.UsuarioService;

@ExtendWith(MockitoExtension.class)
class AntecedenteServiceTest {

	private static final long PACIENTE = 3L;

	private static final long MEDICO = 8L;

	@Mock
	private AntecedenteRepository repository;

	@Mock
	private PacienteService pacienteService;

	@Mock
	private UsuarioService usuarioService;

	@Mock
	private AuditoriaService auditoria;

	@InjectMocks
	private AntecedenteService service;

	private Paciente paciente;

	private Usuario medico;

	@BeforeEach
	void setUp() {
		paciente = new Paciente();
		ReflectionTestUtils.setField(paciente, "id", PACIENTE);
		medico = new Usuario();
		ReflectionTestUtils.setField(medico, "id", MEDICO);
		medico.setNombres("Carlos");
		medico.setApellidos("Díaz");
		TestSeguridad.autenticarComo(MEDICO, "dr.diaz", "MEDICO");
	}

	@AfterEach
	void limpiar() {
		TestSeguridad.limpiar();
	}

	@Test
	void registrarGuardaElAutorElPacienteYLoAudita() {
		when(pacienteService.obtener(PACIENTE)).thenReturn(paciente);
		when(usuarioService.obtener(MEDICO)).thenReturn(medico);

		Antecedente a = service.registrar(PACIENTE, new AntecedenteService.Nuevo(TipoAntecedente.QUIRURGICO,
				"Apendicectomía", "Sin complicaciones", LocalDate.of(2019, 5, 1), null, null, null));

		assertThat(a.getPaciente()).isSameAs(paciente);
		assertThat(a.getRegistradoPor()).isSameAs(medico);
		assertThat(a.getTipo()).isEqualTo(TipoAntecedente.QUIRURGICO);
		assertThat(a.isActivo()).isTrue();
		verify(repository).save(a);
		verify(auditoria).registrarSobrePaciente(eq(AccionAuditoria.CREAR), eq("ANTECEDENTE"), any(), eq(PACIENTE),
				contains("Apendicectomía"));
	}

	@Test
	void listarCompruebaElPacienteYAuditaLaConsulta() {
		Antecedente a = antecedente(true);
		when(repository.findByPacienteIdOrderByActivoDescCreadoEnDesc(PACIENTE)).thenReturn(List.of(a));

		List<AntecedenteResponse> lista = service.listar(PACIENTE);

		assertThat(lista).hasSize(1);
		assertThat(lista.get(0).registradoPor()).isEqualTo("Díaz, Carlos");
		verify(pacienteService).obtener(PACIENTE);
		verify(auditoria).registrarSobrePaciente(AccionAuditoria.VER, "ANTECEDENTE", null, PACIENTE, "Antecedentes");
	}

	@Test
	void inactivarNoBorraYGuardaElMotivo() {
		Antecedente a = antecedente(true);
		when(repository.findById(1L)).thenReturn(Optional.of(a));

		AntecedenteResponse r = service.inactivar(1L, "  Registrado en el paciente equivocado ");

		assertThat(r.activo()).isFalse();
		assertThat(a.getMotivoInactivacion()).isEqualTo("Registrado en el paciente equivocado");
		verify(repository, never()).delete(any());
		verify(auditoria).registrarSobrePaciente(eq(AccionAuditoria.DESACTIVAR), eq("ANTECEDENTE"), eq(1L),
				eq(PACIENTE), contains("paciente equivocado"));
	}

	@Test
	void noSeInactivaDosVeces() {
		when(repository.findById(1L)).thenReturn(Optional.of(antecedente(false)));

		assertThatThrownBy(() -> service.inactivar(1L, "Duplicado")).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("ya está inactivo");
	}

	@Test
	void inactivarUnoInexistenteDaNoEncontrado() {
		when(repository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.inactivar(99L, "x")).isInstanceOf(RecursoNoEncontradoException.class);
	}

	private Antecedente antecedente(boolean activo) {
		Antecedente a = new Antecedente();
		ReflectionTestUtils.setField(a, "id", 1L);
		a.setPaciente(paciente);
		a.setTipo(TipoAntecedente.PERSONAL);
		a.setDescripcion("Hipertensión arterial");
		a.setRegistradoPor(medico);
		a.setActivo(activo);
		return a;
	}

}
