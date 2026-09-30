package com.historiamed.backend.atencion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.historiamed.backend.TestSeguridad;
import com.historiamed.backend.alergia.AlergiaService;
import com.historiamed.backend.atencion.dto.AtencionRequest;
import com.historiamed.backend.atencion.dto.AtencionRequest.ControlRequest;
import com.historiamed.backend.atencion.dto.AtencionRequest.DescansoRequest;
import com.historiamed.backend.atencion.dto.AtencionRequest.ItemPlanRequest;
import com.historiamed.backend.atencion.dto.AtencionResponse;
import com.historiamed.backend.atencion.dto.ControlPendienteResponse;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.catalogo.CatalogoService;
import com.historiamed.backend.cita.Cita;
import com.historiamed.backend.cita.CitaService;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.util.Tiempo;
import com.historiamed.backend.consultorio.Consultorio;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.PacienteService;
import com.historiamed.backend.paciente.Sexo;
import com.historiamed.backend.triaje.TriajeService;
import com.historiamed.backend.usuario.Usuario;
import com.historiamed.backend.usuario.UsuarioService;

/** Tratamiento estructurado de la atención: plan, descanso médico y cita de control (plan.md, sección 5.4). */
@ExtendWith(MockitoExtension.class)
class AtencionPlanTest {

	private static final long ATENCION = 30L;

	private static final long MEDICO = 8L;

	private static final long PACIENTE = 3L;

	@Mock
	private AtencionRepository repository;

	@Mock
	private AdendaRepository adendaRepository;

	@Mock
	private CitaService citaService;

	@Mock
	private TriajeService triajeService;

	@Mock
	private AlergiaService alergiaService;

	@Mock
	private PacienteService pacienteService;

	@Mock
	private CatalogoService catalogoService;

	@Mock
	private UsuarioService usuarioService;

	@Mock
	private AuditoriaService auditoria;

	private AtencionService service;

	private Atencion atencion;

	private final LocalDate hoy = Tiempo.hoy();

	@BeforeEach
	void setUp() {
		service = new AtencionService(repository, adendaRepository, citaService, triajeService, alergiaService,
				pacienteService, catalogoService, usuarioService, new VerificadorAlergias(), auditoria);
		atencion = atencion(ATENCION, PACIENTE);
		lenient().when(repository.findById(ATENCION)).thenReturn(Optional.of(atencion));
		lenient().when(alergiaService.activas(PACIENTE)).thenReturn(List.of());
		TestSeguridad.autenticarComo(MEDICO, "medico1", "MEDICO");
	}

	@AfterEach
	void tearDown() {
		TestSeguridad.limpiar();
	}

	@Test
	void guardaTratamientoExamenesInterconsultaDescansoYControl() {
		AtencionResponse r = service.guardar(ATENCION,
				request(List.of(new ItemPlanRequest(ItemPlan.Tipo.TRATAMIENTO, ItemPlan.Categoria.DIETA,
						" Dieta hiposódica ", null),
						new ItemPlanRequest(ItemPlan.Tipo.EXAMEN, ItemPlan.Categoria.LABORATORIO, "Hemograma completo",
								"en ayunas"),
						new ItemPlanRequest(ItemPlan.Tipo.INTERCONSULTA, ItemPlan.Categoria.OTRO, "Cardiología",
								"soplo sistólico")),
						new DescansoRequest(3, hoy), new ControlRequest(hoy.plusDays(7), "traer resultados")));

		assertThat(r.plan()).hasSize(3);
		assertThat(r.plan().get(0).descripcion()).isEqualTo("Dieta hiposódica");
		// La interconsulta no lleva categoría aunque el cliente la envíe
		assertThat(r.plan().get(2).categoria()).isNull();
		assertThat(r.descanso().dias()).isEqualTo(3);
		// 3 días desde hoy: hoy, mañana y pasado
		assertThat(r.descanso().hasta()).isEqualTo(hoy.plusDays(2));
		assertThat(r.control().fecha()).isEqualTo(hoy.plusDays(7));
	}

	@Test
	void guardarSinPlanDejaLaAtencionSinIndicacionesNiDescanso() {
		atencion.setDescansoDias(2);
		atencion.setDescansoDesde(hoy);

		AtencionResponse r = service.guardar(ATENCION, request(null, null, null));

		assertThat(r.plan()).isEmpty();
		assertThat(r.descanso()).isNull();
		assertThat(r.control()).isNull();
	}

	@Test
	void cadaTipoSoloAdmiteSusCategorias() {
		var examenComoDieta = request(
				List.of(new ItemPlanRequest(ItemPlan.Tipo.EXAMEN, ItemPlan.Categoria.DIETA, "Radiografía", null)), null,
				null);

		assertThatThrownBy(() -> service.guardar(ATENCION, examenComoDieta))
			.isInstanceOfSatisfying(ReglaNegocioException.class,
					e -> assertThat(e.getDetalles()).containsKey("plan[0]"));
	}

	@Test
	void laInterconsultaExigeMotivo() {
		var sinMotivo = request(List.of(new ItemPlanRequest(ItemPlan.Tipo.INTERCONSULTA, null, "Cardiología", " ")),
				null, null);

		assertThatThrownBy(() -> service.guardar(ATENCION, sinMotivo)).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("motivo");
	}

	@Test
	void elDescansoNoEmpiezaAntesDeLaAtencionNiMuchoDespues() {
		assertThatThrownBy(
				() -> service.guardar(ATENCION, request(null, new DescansoRequest(2, hoy.minusDays(1)), null)))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("antes del día de la atención");
		assertThatThrownBy(
				() -> service.guardar(ATENCION, request(null, new DescansoRequest(2, hoy.plusDays(8)), null)))
			.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void elControlDebeSerPosteriorALaAtencionYDentroDeUnAnio() {
		assertThatThrownBy(() -> service.guardar(ATENCION, request(null, null, new ControlRequest(hoy, null))))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("posterior");
		assertThatThrownBy(() -> service.guardar(ATENCION,
				request(null, null, new ControlRequest(hoy.plusYears(1).plusDays(1), null))))
			.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void unControlDejaDeEstarPendienteCuandoElPacienteTieneUnaCitaPosterior() {
		Atencion conCita = atencion(31L, 4L);
		conCita.setControlFecha(hoy.plusDays(3));
		atencion.setControlFecha(hoy.plusDays(5));
		when(repository.findByEstadoAndControlFechaGreaterThanEqualOrderByControlFecha(any(), any()))
			.thenReturn(List.of(conCita, atencion));
		when(citaService.tieneCitaPosterior(4L, hoy)).thenReturn(true);
		when(citaService.tieneCitaPosterior(PACIENTE, hoy)).thenReturn(false);

		List<ControlPendienteResponse> pendientes = service.controlesPendientes();

		assertThat(pendientes).hasSize(1);
		assertThat(pendientes.get(0).atencionId()).isEqualTo(ATENCION);
		assertThat(pendientes.get(0).fechaSugerida()).isEqualTo(hoy.plusDays(5));
		assertThat(pendientes.get(0).vencido()).isFalse();
		assertThat(pendientes.get(0).consultorio()).isEqualTo("Consultorio 1");
	}

	// ------------------------------------------------------------------------------------------------------------

	private static AtencionRequest request(List<ItemPlanRequest> plan, DescansoRequest descanso,
			ControlRequest control) {
		return new AtencionRequest("Dolor de cabeza", null, "anamnesis", "examen", null, null, List.of(), List.of(),
				plan, descanso, control);
	}

	private static Atencion atencion(long id, long pacienteId) {
		Usuario medico = new Usuario();
		ReflectionTestUtils.setField(medico, "id", MEDICO);
		medico.setNombres("Carlos");
		medico.setApellidos("Díaz");
		Paciente paciente = new Paciente();
		ReflectionTestUtils.setField(paciente, "id", pacienteId);
		paciente.setNumeroHc("HC-00000" + pacienteId);
		paciente.setNombres("Rosa");
		paciente.setApellidoPaterno("Quispe");
		paciente.setFechaNacimiento(LocalDate.of(1990, 1, 1));
		paciente.setSexo(Sexo.FEMENINO);
		Consultorio consultorio = new Consultorio();
		ReflectionTestUtils.setField(consultorio, "id", 1L);
		ReflectionTestUtils.setField(consultorio, "nombre", "Consultorio 1");
		Cita cita = new Cita();
		ReflectionTestUtils.setField(cita, "id", id + 100);
		ReflectionTestUtils.setField(cita, "consultorio", consultorio);

		Atencion a = new Atencion();
		ReflectionTestUtils.setField(a, "id", id);
		a.setCita(cita);
		a.setPaciente(paciente);
		a.setMedico(medico);
		a.setInicioEn(Instant.now());
		a.setMotivoConsulta("Dolor de cabeza");
		return a;
	}

}
