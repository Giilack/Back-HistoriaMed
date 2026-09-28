package com.historiamed.backend.cita;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.historiamed.backend.TestSeguridad;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.cita.dto.CitaRequest;
import com.historiamed.backend.cita.dto.LlegadaSinCitaRequest;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.util.Tiempo;
import com.historiamed.backend.consultorio.Consultorio;
import com.historiamed.backend.consultorio.ConsultorioService;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.PacienteService;
import com.historiamed.backend.paciente.Sexo;
import com.historiamed.backend.usuario.Rol;
import com.historiamed.backend.usuario.Usuario;
import com.historiamed.backend.usuario.UsuarioService;

@ExtendWith(MockitoExtension.class)
class CitaServiceTest {

	@Mock
	private CitaRepository repository;

	@Mock
	private PacienteService pacienteService;

	@Mock
	private UsuarioService usuarioService;

	@Mock
	private ConsultorioService consultorioService;

	@Mock
	private AuditoriaService auditoria;

	private CitaService service;

	private final LocalDate hoy = Tiempo.hoy();

	@BeforeEach
	void setUp() {
		service = new CitaService(repository, pacienteService, usuarioService, consultorioService, auditoria);
		TestSeguridad.autenticarComo(7L, "admision1", "ADMISION");
	}

	@AfterEach
	void tearDown() {
		TestSeguridad.limpiar();
	}

	@Test
	void noSeProgramaEnFechaPasada() {
		when(pacienteService.obtener(1L)).thenReturn(paciente());

		assertThatThrownBy(() -> service.programar(request(hoy.minusDays(1), LocalTime.of(9, 0))))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("fecha pasada");
		verify(repository, never()).save(any());
	}

	@Test
	void medicoNoPuedeTenerDosCitasALaMismaHora() {
		prepararProgramacion();
		when(repository.medicoTieneCitaALaHora(eq(20L), eq(hoy.plusDays(1)), eq(LocalTime.of(9, 0)), any(),
				anyLong()))
			.thenReturn(true);

		assertThatThrownBy(() -> service.programar(request(hoy.plusDays(1), LocalTime.of(9, 0))))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("ya tiene una cita");
	}

	@Test
	void pacienteNoPuedeTenerDosTurnosActivosElMismoDiaEnElMismoConsultorio() {
		prepararProgramacion();
		when(repository.pacienteTieneTurnoActivo(eq(1L), eq(hoy.plusDays(1)), eq(30L), any(), anyLong()))
			.thenReturn(true);

		assertThatThrownBy(() -> service.programar(request(hoy.plusDays(1), LocalTime.of(9, 0))))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("ya tiene una cita activa");
	}

	@Test
	void programarDejaLaCitaEnEstadoProgramada() {
		prepararProgramacion();

		var resp = service.programar(request(hoy.plusDays(1), LocalTime.of(9, 0)));

		assertThat(resp.estado()).isEqualTo(EstadoCita.PROGRAMADA);
		assertThat(resp.numeroTurno()).isNull();
		verify(repository).save(any());
	}

	@Test
	void llegadaSinCitaAsignaElSiguienteTurnoDelDia() {
		when(pacienteService.obtener(1L)).thenReturn(paciente());
		when(consultorioService.obtenerActivo(30L)).thenReturn(consultorio());
		when(usuarioService.obtenerMedicoActivo(20L)).thenReturn(medico());
		when(repository.ultimoTurno(hoy, 30L)).thenReturn(4);

		var resp = service.registrarSinCita(new LlegadaSinCitaRequest(1L, 20L, 30L, "Dolor de cabeza"));

		assertThat(resp.estado()).isEqualTo(EstadoCita.EN_ESPERA_TRIAJE);
		assertThat(resp.numeroTurno()).isEqualTo(5);
		assertThat(resp.sinCita()).isTrue();
		assertThat(resp.fecha()).isEqualTo(hoy);
	}

	@Test
	void llegadaSoloElDiaDeLaCita() {
		Cita c = cita(hoy.plusDays(2));
		when(repository.findById(50L)).thenReturn(Optional.of(c));

		assertThatThrownBy(() -> service.registrarLlegada(50L)).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("solo se registra la llegada ese día");
	}

	@Test
	void llegadaDelDiaPasaALaColaDeTriajeConTurno() {
		Cita c = cita(hoy);
		when(repository.findById(50L)).thenReturn(Optional.of(c));
		when(repository.ultimoTurno(hoy, 30L)).thenReturn(0);

		var resp = service.registrarLlegada(50L);

		assertThat(resp.estado()).isEqualTo(EstadoCita.EN_ESPERA_TRIAJE);
		assertThat(resp.numeroTurno()).isEqualTo(1);
		assertThat(resp.llegadaEn()).isNotNull();
	}

	@Test
	void citaFuturaNoSeMarcaComoNoSePresento() {
		when(repository.findById(50L)).thenReturn(Optional.of(cita(hoy.plusDays(1))));

		assertThatThrownBy(() -> service.marcarNoSePresento(50L)).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("cancélela");
	}

	@Test
	void soloSeReprogramaUnaCitaProgramada() {
		Cita c = cita(hoy);
		c.cambiarEstado(EstadoCita.EN_ESPERA_TRIAJE, Instant.now());
		when(repository.findById(50L)).thenReturn(Optional.of(c));

		assertThatThrownBy(() -> service.reprogramar(50L, request(hoy.plusDays(1), LocalTime.of(10, 0))))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("PROGRAMADA");
	}

	@Test
	void listarExigeFechaOPaciente() {
		assertThatThrownBy(() -> service.listar(null, null, null, null, null))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("fecha o el paciente");
	}

	private void prepararProgramacion() {
		when(pacienteService.obtener(1L)).thenReturn(paciente());
		when(usuarioService.obtenerMedicoActivo(20L)).thenReturn(medico());
		when(consultorioService.obtenerActivo(30L)).thenReturn(consultorio());
	}

	private static CitaRequest request(LocalDate fecha, LocalTime hora) {
		return new CitaRequest(1L, 20L, 30L, fecha, hora, "Control");
	}

	private Cita cita(LocalDate fecha) {
		Cita c = new Cita();
		ReflectionTestUtils.setField(c, "id", 50L);
		c.setPaciente(paciente());
		c.setMedico(medico());
		c.setConsultorio(consultorio());
		c.setFecha(fecha);
		c.setHora(LocalTime.of(9, 0));
		c.iniciar(false, Instant.now());
		return c;
	}

	private static Paciente paciente() {
		Paciente p = new Paciente();
		ReflectionTestUtils.setField(p, "id", 1L);
		p.setNumeroHc("HC-000001");
		p.setNombres("Rosa");
		p.setApellidoPaterno("Quispe");
		p.setFechaNacimiento(LocalDate.of(1985, 3, 14));
		p.setSexo(Sexo.FEMENINO);
		return p;
	}

	private static Usuario medico() {
		Usuario u = new Usuario();
		ReflectionTestUtils.setField(u, "id", 20L);
		u.setNombres("Carlos");
		u.setApellidos("Díaz");
		u.setRol(Rol.MEDICO);
		u.setCmp("12345");
		return u;
	}

	private static Consultorio consultorio() {
		Consultorio c = new Consultorio();
		ReflectionTestUtils.setField(c, "id", 30L);
		c.setNombre("Consultorio 1");
		c.setEspecialidad("Medicina General");
		return c;
	}

}
