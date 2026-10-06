package com.historiamed.backend.laboratorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.laboratorio.dto.ResultadoLaboratorioResponse;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.PacienteService;
import com.historiamed.backend.usuario.Usuario;
import com.historiamed.backend.usuario.UsuarioService;

@ExtendWith(MockitoExtension.class)
class LaboratorioServiceTest {

	private static final long PACIENTE = 3L;

	private static final long TRIAJE = 5L;

	@Mock
	private ResultadoLaboratorioRepository repository;

	@Mock
	private PacienteService pacienteService;

	@Mock
	private UsuarioService usuarioService;

	@Mock
	private AuditoriaService auditoria;

	@InjectMocks
	private LaboratorioService service;

	private Paciente paciente;

	private Usuario autor;

	@BeforeEach
	void setUp() {
		paciente = new Paciente();
		ReflectionTestUtils.setField(paciente, "id", PACIENTE);
		autor = new Usuario();
		ReflectionTestUtils.setField(autor, "id", TRIAJE);
		autor.setNombres("Pedro");
		autor.setApellidos("Salas");
		TestSeguridad.autenticarComo(TRIAJE, "triaje.demo", "TRIAJE");
	}

	@AfterEach
	void limpiar() {
		TestSeguridad.limpiar();
	}

	@Test
	void registrarGuardaElResultadoConSuUnidadEnLaAuditoria() {
		when(pacienteService.obtener(PACIENTE)).thenReturn(paciente);
		when(usuarioService.obtener(TRIAJE)).thenReturn(autor);

		ResultadoLaboratorio r = service.registrar(PACIENTE, new LaboratorioService.Nuevo("Hemoglobina", "13.2",
				"g/dL", "12 - 16", LocalDate.of(2026, 9, 20), null));

		assertThat(r.getExamen()).isEqualTo("Hemoglobina");
		assertThat(r.getRegistradoPor()).isSameAs(autor);
		verify(repository).save(r);
		verify(auditoria).registrarSobrePaciente(eq(AccionAuditoria.CREAR), eq("RESULTADO_LABORATORIO"), any(),
				eq(PACIENTE), eq("Hemoglobina: 13.2 g/dL"));
	}

	@Test
	void sinUnidadLaAuditoriaNoAgregaEspacios() {
		when(pacienteService.obtener(PACIENTE)).thenReturn(paciente);
		when(usuarioService.obtener(TRIAJE)).thenReturn(autor);

		service.registrar(PACIENTE, new LaboratorioService.Nuevo("Grupo sanguíneo", "O+", null, null, null, null));

		verify(auditoria).registrarSobrePaciente(eq(AccionAuditoria.CREAR), eq("RESULTADO_LABORATORIO"), any(),
				eq(PACIENTE), eq("Grupo sanguíneo: O+"));
	}

	@Test
	void listarAuditaLaConsultaDelPaciente() {
		when(repository.delPaciente(PACIENTE)).thenReturn(List.of(resultado(true)));

		List<ResultadoLaboratorioResponse> lista = service.listar(PACIENTE);

		assertThat(lista).singleElement().satisfies(r -> assertThat(r.registradoPor()).isEqualTo("Salas, Pedro"));
		verify(auditoria).registrarSobrePaciente(AccionAuditoria.VER, "RESULTADO_LABORATORIO", null, PACIENTE,
				"Resultados de laboratorio");
	}

	@Test
	void inactivarConservaElResultadoConSuMotivo() {
		ResultadoLaboratorio r = resultado(true);
		when(repository.findById(1L)).thenReturn(Optional.of(r));

		ResultadoLaboratorioResponse respuesta = service.inactivar(1L, " Valor de otro paciente ");

		assertThat(respuesta.activo()).isFalse();
		assertThat(r.getMotivoInactivacion()).isEqualTo("Valor de otro paciente");
		verify(repository, never()).delete(any());
	}

	@Test
	void noSeInactivaDosVeces() {
		when(repository.findById(1L)).thenReturn(Optional.of(resultado(false)));

		assertThatThrownBy(() -> service.inactivar(1L, "x")).isInstanceOf(ReglaNegocioException.class);
	}

	private ResultadoLaboratorio resultado(boolean activo) {
		ResultadoLaboratorio r = new ResultadoLaboratorio();
		ReflectionTestUtils.setField(r, "id", 1L);
		r.setPaciente(paciente);
		r.setExamen("Glucosa");
		r.setValor("98");
		r.setUnidad("mg/dL");
		r.setRegistradoPor(autor);
		r.setActivo(activo);
		return r;
	}

}
