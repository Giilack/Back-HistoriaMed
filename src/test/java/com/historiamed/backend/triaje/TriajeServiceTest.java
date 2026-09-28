package com.historiamed.backend.triaje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.historiamed.backend.TestSeguridad;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.cita.Cita;
import com.historiamed.backend.cita.CitaService;
import com.historiamed.backend.cita.EstadoCita;
import com.historiamed.backend.cita.Prioridad;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.PacienteService;
import com.historiamed.backend.paciente.Sexo;
import com.historiamed.backend.triaje.dto.TriajeRequest;
import com.historiamed.backend.triaje.dto.TriajeResponse;
import com.historiamed.backend.usuario.Rol;
import com.historiamed.backend.usuario.Usuario;
import com.historiamed.backend.usuario.UsuarioService;

@ExtendWith(MockitoExtension.class)
class TriajeServiceTest {

	@Mock
	private TriajeRepository repository;

	@Mock
	private CitaService citaService;

	@Mock
	private PacienteService pacienteService;

	@Mock
	private UsuarioService usuarioService;

	@Mock
	private AuditoriaService auditoria;

	private TriajeService service;

	@BeforeEach
	void setUp() {
		service = new TriajeService(repository, citaService, pacienteService, usuarioService, new EvaluadorTriaje(),
				auditoria);
		TestSeguridad.autenticarComo(9L, "triaje1", "TRIAJE");
	}

	@AfterEach
	void tearDown() {
		TestSeguridad.limpiar();
	}

	@Test
	void registrarPasaLaCitaALaColaDelMedicoConLaPrioridadSugerida() {
		Cita cita = citaEnColaDeTriaje(Sexo.FEMENINO);
		when(citaService.obtenerEntidad(50L)).thenReturn(cita);
		when(usuarioService.obtener(9L)).thenReturn(enfermera());

		TriajeResponse t = service.registrar(50L, request(88, null, null, false));

		assertThat(t.prioridadSugerida()).isEqualTo(Prioridad.URGENTE);
		assertThat(t.prioridad()).isEqualTo(Prioridad.URGENTE);
		assertThat(t.alertas()).extracting("codigo").containsExactly("SATURACION_MUY_BAJA");
		assertThat(cita.getEstado()).isEqualTo(EstadoCita.EN_ESPERA_CONSULTA);
		assertThat(cita.getPrioridad()).isEqualTo(Prioridad.URGENTE);
		assertThat(cita.getTriajeEn()).isNotNull();
	}

	@Test
	void bajarLaPrioridadSugeridaExigeJustificacion() {
		when(citaService.obtenerEntidad(50L)).thenReturn(citaEnColaDeTriaje(Sexo.FEMENINO));

		assertThatThrownBy(() -> service.registrar(50L, request(88, Prioridad.NORMAL, null, false)))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("justificación");
		verify(repository, never()).save(any());
	}

	@Test
	void conJustificacionSePuedeBajarLaPrioridad() {
		when(citaService.obtenerEntidad(50L)).thenReturn(citaEnColaDeTriaje(Sexo.FEMENINO));
		when(usuarioService.obtener(9L)).thenReturn(enfermera());

		TriajeResponse t = service.registrar(50L,
				request(88, Prioridad.PREFERENTE, "Paciente con EPOC, saturación habitual 88 %", false));

		assertThat(t.prioridad()).isEqualTo(Prioridad.PREFERENTE);
		assertThat(t.justificacionPrioridad()).contains("EPOC");
	}

	@Test
	void noSeTriaUnaCitaQueNoEstaEnLaColaDeTriaje() {
		Cita cita = citaEnColaDeTriaje(Sexo.FEMENINO);
		cita.completarTriaje(Prioridad.NORMAL, Instant.now());
		when(citaService.obtenerEntidad(50L)).thenReturn(cita);

		assertThatThrownBy(() -> service.registrar(50L, request(98, null, null, false)))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("no está en la cola de triaje");
	}

	@Test
	void noSeRegistraDosVecesElTriajeDeUnaCita() {
		when(citaService.obtenerEntidad(50L)).thenReturn(citaEnColaDeTriaje(Sexo.FEMENINO));
		when(repository.existsByCitaId(50L)).thenReturn(true);

		assertThatThrownBy(() -> service.registrar(50L, request(98, null, null, false)))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("ya tiene un triaje");
	}

	@Test
	void unHombreNoPuedeMarcarseComoGestante() {
		when(citaService.obtenerEntidad(50L)).thenReturn(citaEnColaDeTriaje(Sexo.MASCULINO));

		assertThatThrownBy(() -> service.evaluar(50L, request(98, null, null, true)))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("gestante");
	}

	@Test
	void presionIncompletaOInvertidaSeRechaza() {
		when(citaService.obtenerEntidad(50L)).thenReturn(citaEnColaDeTriaje(Sexo.FEMENINO));

		var soloSistolica = new TriajeRequest("Cefalea", 120, null, 75, 16, new BigDecimal("36.8"), 98,
				new BigDecimal("70"), null, null, false, false, null, null, null);
		assertThatThrownBy(() -> service.evaluar(50L, soloSistolica)).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("sistólica y la diastólica");

		var invertida = new TriajeRequest("Cefalea", 80, 120, 75, 16, new BigDecimal("36.8"), 98,
				new BigDecimal("70"), null, null, false, false, null, null, null);
		assertThatThrownBy(() -> service.evaluar(50L, invertida)).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("mayor que la diastólica");
	}

	private static TriajeRequest request(int saturacion, Prioridad prioridad, String justificacion,
			boolean gestante) {
		return new TriajeRequest("Dificultad para respirar", 120, 80, 88, 18, new BigDecimal("37.0"), saturacion,
				new BigDecimal("68.5"), new BigDecimal("160"), null, gestante, false, prioridad, justificacion, null);
	}

	private static Cita citaEnColaDeTriaje(Sexo sexo) {
		Paciente p = new Paciente();
		ReflectionTestUtils.setField(p, "id", 1L);
		p.setNombres("Rosa");
		p.setApellidoPaterno("Quispe");
		p.setFechaNacimiento(LocalDate.of(1985, 3, 14));
		p.setSexo(sexo);
		Cita c = new Cita();
		ReflectionTestUtils.setField(c, "id", 50L);
		c.setPaciente(p);
		c.setHora(LocalTime.of(9, 0));
		c.iniciar(true, Instant.now());
		return c;
	}

	private static Usuario enfermera() {
		Usuario u = new Usuario();
		ReflectionTestUtils.setField(u, "id", 9L);
		u.setNombres("Pedro");
		u.setApellidos("Salas");
		u.setRol(Rol.TRIAJE);
		return u;
	}

}
