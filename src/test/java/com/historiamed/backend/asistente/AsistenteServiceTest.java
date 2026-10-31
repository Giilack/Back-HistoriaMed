package com.historiamed.backend.asistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.historiamed.backend.alergia.Alergia;
import com.historiamed.backend.alergia.AlergiaService;
import com.historiamed.backend.alergia.GravedadAlergia;
import com.historiamed.backend.alergia.TipoAlergia;
import com.historiamed.backend.antecedente.AntecedenteService;
import com.historiamed.backend.asistente.dto.PreguntaAsistenteRequest;
import com.historiamed.backend.asistente.dto.PreguntaAsistenteRequest.Autor;
import com.historiamed.backend.asistente.dto.PreguntaAsistenteRequest.Turno;
import com.historiamed.backend.asistente.dto.RespuestaAsistente;
import com.historiamed.backend.atencion.AtencionService;
import com.historiamed.backend.atencion.EstadoAtencion;
import com.historiamed.backend.atencion.TipoDiagnostico;
import com.historiamed.backend.atencion.VerificadorAlergias;
import com.historiamed.backend.atencion.ViaAdministracion;
import com.historiamed.backend.atencion.dto.AtencionResponse;
import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.catalogo.CatalogoService;
import com.historiamed.backend.catalogo.Medicamento;
import com.historiamed.backend.ia.ErrorIaException;
import com.historiamed.backend.ia.ProveedorIA;
import com.historiamed.backend.laboratorio.LaboratorioService;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.PacienteService;
import com.historiamed.backend.paciente.Sexo;
import com.historiamed.backend.triaje.TriajeService;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AsistenteServiceTest {

	private static final long PACIENTE = 1L;

	@Mock
	private ProveedorIA ia;

	@Mock
	private PacienteService pacientes;

	@Mock
	private AtencionService atenciones;

	@Mock
	private AlergiaService alergias;

	@Mock
	private AntecedenteService antecedentes;

	@Mock
	private LaboratorioService laboratorio;

	@Mock
	private TriajeService triajes;

	@Mock
	private CatalogoService catalogo;

	@Mock
	private AuditoriaService auditoria;

	private AsistenteService service;

	@BeforeEach
	void preparar() {
		service = new AsistenteService(ia, pacientes, atenciones, alergias, antecedentes, laboratorio, triajes, catalogo,
				new VerificadorAlergias(), auditoria, JsonMapper.builder().build());
	}

	/** Paciente con datos que identifican a la persona: ninguno debe llegar a la IA. */
	private void pacienteConHistoria(List<Alergia> activas) {
		Paciente p = new Paciente();
		p.setNombres("Rosa María");
		p.setApellidoPaterno("Quispe");
		p.setNumeroHc("HC-000001");
		p.setNumeroDocumento("45678912");
		p.setTelefono("987654321");
		p.setDireccion("Av. Los Olivos 123");
		p.setFechaNacimiento(LocalDate.of(1985, 3, 14));
		p.setSexo(Sexo.FEMENINO);
		when(pacientes.obtener(PACIENTE)).thenReturn(p);
		when(alergias.activas(PACIENTE)).thenReturn(activas);
		when(atenciones.historia(PACIENTE)).thenReturn(List.of(atencionFaringitis()));
		when(antecedentes.listar(PACIENTE)).thenReturn(List.of());
		when(laboratorio.listar(PACIENTE)).thenReturn(List.of());
		when(triajes.historial(PACIENTE)).thenReturn(List.of());
		when(ia.disponible()).thenReturn(true);
		// No todas las pruebas llegan a usar el modelo (por ejemplo, si la respuesta es inválida)
		lenient().when(ia.modelo()).thenReturn("modelo-prueba");
	}

	private static AtencionResponse atencionFaringitis() {
		return new AtencionResponse(10L, 3L,
				new AtencionResponse.PacienteAtencion(PACIENTE, "HC-000001", "Quispe, Rosa María", "41 años", Sexo.FEMENINO),
				new AtencionResponse.MedicoAtencion(4L, "Díaz Mendoza, Carlos", "045123"), EstadoAtencion.CERRADA,
				Instant.parse("2026-03-12T15:00:00Z"), Instant.parse("2026-03-12T15:20:00Z"), "Dolor de garganta",
				"2 días", "Odinofagia y fiebre", "Faringe congestiva", null, "Abundantes líquidos",
				List.of(new AtencionResponse.DiagnosticoResponse("J02.9", "Faringitis aguda", TipoDiagnostico.PRESUNTIVO,
						true)),
				List.of(new AtencionResponse.ItemRecetaResponse(1L, "Paracetamol 500 mg · Tableta", "1 tableta",
						ViaAdministracion.ORAL, "cada 8 horas", "3 días", 9, null, false, null)),
				List.of(), null, null, List.of());
	}

	private static Alergia alergia(String sustancia, GravedadAlergia gravedad) {
		Alergia a = new Alergia();
		a.setTipo(TipoAlergia.MEDICAMENTO);
		a.setSustancia(sustancia);
		a.setReaccion("Anafilaxia");
		a.setGravedad(gravedad);
		return a;
	}

	private static Medicamento medicamento(String nombre, String principioActivo, String grupo) {
		Medicamento m = new Medicamento();
		ReflectionTestUtils.setField(m, "nombre", nombre);
		ReflectionTestUtils.setField(m, "principioActivo", principioActivo);
		ReflectionTestUtils.setField(m, "concentracion", "500 mg");
		ReflectionTestUtils.setField(m, "formaFarmaceutica", "Cápsula");
		ReflectionTestUtils.setField(m, "grupo", grupo);
		return m;
	}

	private void iaResponde(String json) {
		when(ia.responderJson(anyString(), anyString(), any())).thenReturn(json);
	}

	private static PreguntaAsistenteRequest pregunta(String texto) {
		return new PreguntaAsistenteRequest(texto, List.of());
	}

	@Test
	void laIaRecibeLaHistoriaConIdentificadoresDeFuenteYSinDatosDeLaPersona() {
		pacienteConHistoria(List.of(alergia("Penicilina", GravedadAlergia.SEVERA)));
		iaResponde("{\"respuesta\":\"Paracetamol.\",\"fuentes\":[\"A1\"],\"esRecomendacion\":false,\"medicamentosSugeridos\":[]}");

		service.preguntar(PACIENTE, pregunta("¿Qué medicamentos se le recetaron antes?"));

		ArgumentCaptor<String> contenido = ArgumentCaptor.forClass(String.class);
		verify(ia).responderJson(anyString(), contenido.capture(), any());
		assertThat(contenido.getValue()).contains("[AL1]", "Penicilina", "[A1]", "J02.9", "Paracetamol", "femenino",
				"PREGUNTA DEL MÉDICO: ¿Qué medicamentos se le recetaron antes?")
			.doesNotContain("Rosa", "Quispe", "HC-000001", "45678912", "987654321", "Los Olivos");
	}

	@Test
	void citaSoloFuentesQueExistenEnLaHistoria() {
		pacienteConHistoria(List.of(alergia("Penicilina", GravedadAlergia.SEVERA)));
		iaResponde("{\"respuesta\":\"Faringitis.\",\"fuentes\":[\"A1\",\"Z9\",\"[al1]\",\"A1\"],\"esRecomendacion\":false,"
				+ "\"medicamentosSugeridos\":[]}");

		RespuestaAsistente r = service.preguntar(PACIENTE, pregunta("¿Qué diagnósticos tiene?"));

		assertThat(r.fuentes()).extracting(RespuestaAsistente.Fuente::id).containsExactly("A1", "AL1");
		assertThat(r.fuentes().get(0).etiqueta()).isEqualTo("Atención del 12/03/2026, Díaz Mendoza, Carlos");
		assertThat(r.sugerencia()).isFalse();
		assertThat(r.advertencias()).isEmpty();
	}

	@Test
	void sinRespuestaDeLaIaIndicaQueNoHayRegistro() {
		pacienteConHistoria(List.of());
		iaResponde("{\"respuesta\":\" \",\"fuentes\":[],\"esRecomendacion\":false,\"medicamentosSugeridos\":[]}");

		RespuestaAsistente r = service.preguntar(PACIENTE, pregunta("¿Cuál es su grupo sanguíneo?"));

		assertThat(r.respuesta()).isEqualTo(AsistenteService.SIN_REGISTRO);
		assertThat(r.fuentes()).isEmpty();
	}

	@Test
	void unaSugerenciaConUnMedicamentoAlQueEsAlergicoLlevaAlertaDeterminista() {
		pacienteConHistoria(List.of(alergia("Penicilina", GravedadAlergia.SEVERA)));
		when(catalogo.buscarMedicamentos("amoxicilina"))
			.thenReturn(List.of(medicamento("Amoxicilina", "amoxicilina", "PENICILINAS")));
		iaResponde("{\"respuesta\":\"Podría usarse amoxicilina.\",\"fuentes\":[\"A1\"],\"esRecomendacion\":true,"
				+ "\"medicamentosSugeridos\":[\"amoxicilina\"]}");

		RespuestaAsistente r = service.preguntar(PACIENTE, pregunta("¿Qué antibiótico le puedo dar?"));

		assertThat(r.sugerencia()).isTrue();
		assertThat(r.advertencias()).hasSize(1);
		assertThat(r.advertencias().get(0)).startsWith("Alerta de alergia: Amoxicilina");
	}

	@Test
	void unMedicamentoFueraDelCatalogoSeComparaPorNombreConLaAlergia() {
		pacienteConHistoria(List.of(alergia("Ibuprofeno", GravedadAlergia.MODERADA)));
		when(catalogo.buscarMedicamentos("ibuprofeno en gel")).thenReturn(List.of());
		iaResponde("{\"respuesta\":\"Ibuprofeno tópico.\",\"fuentes\":[],\"esRecomendacion\":true,"
				+ "\"medicamentosSugeridos\":[\"ibuprofeno en gel\"]}");

		RespuestaAsistente r = service.preguntar(PACIENTE, pregunta("¿Algo para el dolor?"));

		assertThat(r.advertencias()).singleElement().asString().contains("alergia registrada a Ibuprofeno");
	}

	@Test
	void sinUnaSugerenciaNoSeRevisanMedicamentos() {
		pacienteConHistoria(List.of(alergia("Penicilina", GravedadAlergia.SEVERA)));
		iaResponde("{\"respuesta\":\"Tiene alergia a penicilina.\",\"fuentes\":[\"AL1\"],\"esRecomendacion\":false,"
				+ "\"medicamentosSugeridos\":[\"amoxicilina\"]}");

		RespuestaAsistente r = service.preguntar(PACIENTE, pregunta("¿Tiene alergias?"));

		assertThat(r.advertencias()).isEmpty();
		verify(catalogo, never()).buscarMedicamentos(anyString());
	}

	@Test
	void laConversacionPreviaSeEnviaParaDarContexto() {
		pacienteConHistoria(List.of());
		iaResponde("{\"respuesta\":\"3 días.\",\"fuentes\":[\"A1\"],\"esRecomendacion\":false,\"medicamentosSugeridos\":[]}");

		service.preguntar(PACIENTE, new PreguntaAsistenteRequest("¿Por cuántos días?",
				List.of(new Turno(Autor.MEDICO, "¿Qué se le recetó?"), new Turno(Autor.ASISTENTE, "Paracetamol."))));

		ArgumentCaptor<String> contenido = ArgumentCaptor.forClass(String.class);
		verify(ia).responderJson(anyString(), contenido.capture(), any());
		assertThat(contenido.getValue()).contains("Médico: ¿Qué se le recetó?", "Asistente: Paracetamol.");
	}

	@Test
	void seAuditaLaConsultaSinGuardarSuContenidoClinico() {
		pacienteConHistoria(List.of());
		iaResponde("{\"respuesta\":\"Faringitis.\",\"fuentes\":[\"A1\"],\"esRecomendacion\":false,\"medicamentosSugeridos\":[]}");

		service.preguntar(PACIENTE, pregunta("¿Tuvo faringitis?"));

		ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
		verify(auditoria).registrarSobrePaciente(eq(AccionAuditoria.CONSULTA_IA), eq(AsistenteService.RECURSO), isNull(),
				eq(PACIENTE), detalle.capture());
		assertThat(detalle.getValue()).contains("modelo-prueba", "1 fuente").doesNotContain("faringitis", "Faringitis");
	}

	@Test
	void sinIaConfiguradaNoConsultaNiAudita() {
		when(ia.disponible()).thenReturn(false);
		var solicitud = pregunta("¿Tiene alergias?");

		assertThatThrownBy(() -> service.preguntar(PACIENTE, solicitud))
			.isInstanceOf(ErrorIaException.class)
			.hasMessageContaining("no está disponible");
		verify(ia, never()).responderJson(anyString(), anyString(), any());
		verify(auditoria, never()).registrarSobrePaciente(any(), anyString(), any(), any(), anyString());
	}

	@Test
	void unaRespuestaConFormatoInvalidoSeInformaComoErrorDeIa() {
		pacienteConHistoria(List.of());
		iaResponde("esto no es JSON");
		var consulta = pregunta("¿Tiene alergias?");

		assertThatThrownBy(() -> service.preguntar(PACIENTE, consulta)).isInstanceOf(ErrorIaException.class)
			.hasMessageContaining("formato");
	}

}
