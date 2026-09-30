package com.historiamed.backend.extraccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.historiamed.backend.TestSeguridad;
import com.historiamed.backend.alergia.Alergia;
import com.historiamed.backend.alergia.AlergiaService;
import com.historiamed.backend.alergia.GravedadAlergia;
import com.historiamed.backend.alergia.TipoAlergia;
import com.historiamed.backend.alergia.dto.AlergiaRequest;
import com.historiamed.backend.antecedente.AntecedenteService;
import com.historiamed.backend.antecedente.TipoAntecedente;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.catalogo.CatalogoService;
import com.historiamed.backend.catalogo.Cie10;
import com.historiamed.backend.common.exception.AccesoProhibidoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.documento.Documento;
import com.historiamed.backend.documento.DocumentoService;
import com.historiamed.backend.extraccion.ExtraccionItem.Categoria;
import com.historiamed.backend.extraccion.dto.ExtraccionResponse;
import com.historiamed.backend.extraccion.dto.ItemRequest;
import com.historiamed.backend.laboratorio.LaboratorioService;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.usuario.Usuario;
import com.historiamed.backend.usuario.UsuarioService;

@ExtendWith(MockitoExtension.class)
class ExtraccionServiceTest {

	private static final long PACIENTE = 3L;

	private static final long DOCUMENTO = 9L;

	private static final long REVISION = 20L;

	private static final long TRIAJE = 5L;

	private static final long MEDICO = 8L;

	@Mock
	private ExtraccionRepository repository;

	@Mock
	private DocumentoService documentoService;

	@Mock
	private CatalogoService catalogoService;

	@Mock
	private AlergiaService alergiaService;

	@Mock
	private AntecedenteService antecedenteService;

	@Mock
	private LaboratorioService laboratorioService;

	@Mock
	private UsuarioService usuarioService;

	@Mock
	private AuditoriaService auditoria;

	private ExtraccionService service;

	private Documento documento;

	@BeforeEach
	void setUp() {
		service = new ExtraccionService(repository, documentoService, catalogoService, alergiaService,
				antecedenteService, laboratorioService, usuarioService, auditoria);
		Paciente paciente = new Paciente();
		ReflectionTestUtils.setField(paciente, "id", PACIENTE);
		documento = new Documento();
		ReflectionTestUtils.setField(documento, "id", DOCUMENTO);
		documento.setPaciente(paciente);
		documento.setNombreOriginal("hemograma.pdf");
		documento.setFechaDocumento(LocalDate.of(2026, 9, 1));
		lenient().when(usuarioService.obtener(TRIAJE)).thenReturn(usuario(TRIAJE));
		lenient().when(usuarioService.obtener(MEDICO)).thenReturn(usuario(MEDICO));
		comoTriaje();
	}

	@AfterEach
	void tearDown() {
		TestSeguridad.limpiar();
	}

	@Test
	void iniciarCreaUnaRevisionManualPendiente() {
		when(documentoService.obtener(DOCUMENTO)).thenReturn(documento);
		when(repository.findByDocumentoIdAndEstadoNot(DOCUMENTO, Extraccion.Estado.RECHAZADA))
			.thenReturn(Optional.empty());

		ExtraccionResponse r = service.iniciar(DOCUMENTO);

		assertThat(r.origen()).isEqualTo(Extraccion.Origen.MANUAL);
		assertThat(r.estado()).isEqualTo(Extraccion.Estado.PENDIENTE_REVISION);
		assertThat(r.pacienteId()).isEqualTo(PACIENTE);
		verify(repository).save(any(Extraccion.class));
	}

	@Test
	void iniciarDevuelveLaRevisionVigenteEnLugarDeCrearOtra() {
		Extraccion vigente = revision();
		when(documentoService.obtener(DOCUMENTO)).thenReturn(documento);
		when(repository.findByDocumentoIdAndEstadoNot(DOCUMENTO, Extraccion.Estado.RECHAZADA))
			.thenReturn(Optional.of(vigente));

		assertThat(service.iniciar(DOCUMENTO).id()).isEqualTo(REVISION);
		verify(repository, never()).save(any());
	}

	@Test
	void unDocumentoAnuladoNoSeRevisa() {
		documento.setEstado(Documento.Estado.ANULADO);
		documento.setMotivoAnulacion("de otro paciente");
		when(documentoService.obtener(DOCUMENTO)).thenReturn(documento);
		when(repository.findByDocumentoIdAndEstadoNot(DOCUMENTO, Extraccion.Estado.RECHAZADA))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.iniciar(DOCUMENTO)).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("anulado");
	}

	@Test
	void laboratorioExigeExamenYResultado() {
		conRevision(revision());

		assertThatThrownBy(() -> service.agregarItem(REVISION, laboratorio("Hemoglobina", null)))
			.isInstanceOfSatisfying(ReglaNegocioException.class,
					e -> assertThat(e.getDetalles()).containsKey("valor"));
	}

	@Test
	void agregarUnDatoLoNormalizaYLoDejaPropuesto() {
		Extraccion e = revision();
		conRevision(e);

		service.agregarItem(REVISION, laboratorio("  Hemoglobina   glicosilada ", " 7,2 "));

		ExtraccionItem item = e.getItems().get(0);
		assertThat(item.getDescripcion()).isEqualTo("Hemoglobina glicosilada");
		assertThat(item.getValor()).isEqualTo("7,2");
		assertThat(item.getEstado()).isEqualTo(ExtraccionItem.Estado.PROPUESTO);
		assertThat(item.getCreadoPor().getId()).isEqualTo(TRIAJE);
	}

	@Test
	void elDiagnosticoTomaSuDescripcionDelCatalogo() {
		Extraccion e = revision();
		conRevision(e);
		Cie10 cie = new Cie10();
		ReflectionTestUtils.setField(cie, "codigo", "E11");
		ReflectionTestUtils.setField(cie, "descripcion", "Diabetes mellitus tipo 2");
		when(catalogoService.obtenerCie10("E11")).thenReturn(cie);

		service.agregarItem(REVISION, new ItemRequest(Categoria.DIAGNOSTICO, "texto libre que se ignora", null, null,
				null, null, "E11", null, null, null, null, null, null, null));

		assertThat(e.getItems().get(0).getDescripcion()).isEqualTo("Diabetes mellitus tipo 2");
		assertThat(e.getItems().get(0).getCie10()).isSameAs(cie);
	}

	@Test
	void elAntecedenteSoloAdmiteLosTiposQueSeEligenAMano() {
		conRevision(revision());
		ItemRequest req = new ItemRequest(Categoria.ANTECEDENTE, "Apendicectomía", null, null, null, null, null, null,
				null, null, null, TipoAntecedente.MEDICACION_HABITUAL, null, null);

		assertThatThrownBy(() -> service.agregarItem(REVISION, req)).isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void elMedicoQueCambiaUnDatoAjenoLoMarcaComoCorregido() {
		Extraccion e = revision();
		ExtraccionItem item = item(e, 1L, Categoria.LABORATORIO, "Glucosa", TRIAJE);
		conRevision(e);
		comoMedico();

		service.editarItem(REVISION, 1L, laboratorio("Glucosa", "126"));

		assertThat(item.isCorregido()).isTrue();
		assertThat(item.getValor()).isEqualTo("126");
	}

	@Test
	void quienEditaSuPropioDatoNoLoMarcaComoCorregido() {
		Extraccion e = revision();
		ExtraccionItem item = item(e, 1L, Categoria.LABORATORIO, "Glucosa", TRIAJE);
		conRevision(e);

		service.editarItem(REVISION, 1L, laboratorio("Glucosa", "126"));

		assertThat(item.isCorregido()).isFalse();
	}

	@Test
	void triajeNoPuedeQuitarUnDatoDeOtraPersona() {
		Extraccion e = revision();
		item(e, 1L, Categoria.OTRO, "Nota", MEDICO);
		conRevision(e);

		assertThatThrownBy(() -> service.eliminarItem(REVISION, 1L)).isInstanceOf(AccesoProhibidoException.class);
		assertThat(e.getItems()).hasSize(1);
	}

	@Test
	void validarPasaLosAceptadosALaHistoriaYDescartaElResto() {
		Extraccion e = revision();
		ExtraccionItem alergia = item(e, 1L, Categoria.ALERGIA, "Penicilina", TRIAJE);
		alergia.setTipoAlergia(TipoAlergia.MEDICAMENTO);
		alergia.setGravedad(GravedadAlergia.SEVERA);
		alergia.setDetalle("urticaria");
		ExtraccionItem laboratorio = item(e, 2L, Categoria.LABORATORIO, "Hemoglobina", TRIAJE);
		laboratorio.setValor("11.2");
		laboratorio.setUnidad("g/dL");
		laboratorio.setCorregido(true);
		ExtraccionItem antecedente = item(e, 3L, Categoria.ANTECEDENTE, "Apendicectomía", TRIAJE);
		antecedente.setTipoAntecedente(TipoAntecedente.QUIRURGICO);
		ExtraccionItem descartado = item(e, 4L, Categoria.LABORATORIO, "Glucosa", TRIAJE);
		conRevision(e);
		when(alergiaService.activas(PACIENTE)).thenReturn(List.of());
		comoMedico();

		ExtraccionResponse r = service.validar(REVISION, Set.of(1L, 2L, 3L));

		assertThat(r.estado()).isEqualTo(Extraccion.Estado.VALIDADA);
		assertThat(e.getRevisadoPor().getId()).isEqualTo(MEDICO);
		assertThat(e.getRevisadoEn()).isNotNull();
		assertThat(alergia.getEstado()).isEqualTo(ExtraccionItem.Estado.ACEPTADO);
		assertThat(laboratorio.getEstado()).isEqualTo(ExtraccionItem.Estado.CORREGIDO);
		assertThat(descartado.getEstado()).isEqualTo(ExtraccionItem.Estado.DESCARTADO);

		verify(alergiaService).registrar(PACIENTE,
				new AlergiaRequest(TipoAlergia.MEDICAMENTO, "Penicilina", "urticaria", GravedadAlergia.SEVERA));
		ArgumentCaptor<LaboratorioService.Nuevo> resultado = ArgumentCaptor.forClass(LaboratorioService.Nuevo.class);
		verify(laboratorioService).registrar(eq(PACIENTE), resultado.capture());
		assertThat(resultado.getValue().examen()).isEqualTo("Hemoglobina");
		// Sin fecha propia, el resultado toma la del documento
		assertThat(resultado.getValue().fecha()).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(resultado.getValue().documento()).isSameAs(documento);
		ArgumentCaptor<AntecedenteService.Nuevo> nuevo = ArgumentCaptor.forClass(AntecedenteService.Nuevo.class);
		verify(antecedenteService).registrar(eq(PACIENTE), nuevo.capture());
		assertThat(nuevo.getValue().tipo()).isEqualTo(TipoAntecedente.QUIRURGICO);
	}

	@Test
	void unaAlergiaQueElPacienteYaTieneNoSeDuplica() {
		Extraccion e = revision();
		ExtraccionItem alergia = item(e, 1L, Categoria.ALERGIA, "penicilina", TRIAJE);
		alergia.setTipoAlergia(TipoAlergia.MEDICAMENTO);
		alergia.setGravedad(GravedadAlergia.SEVERA);
		conRevision(e);
		Alergia existente = new Alergia();
		existente.setSustancia("Penicilina");
		when(alergiaService.activas(PACIENTE)).thenReturn(List.of(existente));
		comoMedico();

		service.validar(REVISION, Set.of(1L));

		assertThat(alergia.getEstado()).isEqualTo(ExtraccionItem.Estado.ACEPTADO);
		verify(alergiaService, never()).registrar(any(), any());
	}

	@Test
	void losDatosDeCategoriaOtroNoPasanALaHistoria() {
		Extraccion e = revision();
		item(e, 1L, Categoria.OTRO, "Paciente refiere viaje reciente", TRIAJE);
		conRevision(e);
		comoMedico();

		service.validar(REVISION, Set.of(1L));

		verifyNoInteractions(alergiaService, antecedenteService, laboratorioService);
	}

	@Test
	void noSeValidaUnaRevisionSinDatosNiConDatosAjenos() {
		Extraccion e = revision();
		conRevision(e);
		comoMedico();

		assertThatThrownBy(() -> service.validar(REVISION, Set.of())).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("no tiene datos");

		item(e, 1L, Categoria.OTRO, "Nota", TRIAJE);
		assertThatThrownBy(() -> service.validar(REVISION, Set.of(99L))).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("no pertenecen");
		assertThat(e.getEstado()).isEqualTo(Extraccion.Estado.PENDIENTE_REVISION);
	}

	@Test
	void rechazarDescartaTodoYNoTocaLaHistoria() {
		Extraccion e = revision();
		ExtraccionItem item = item(e, 1L, Categoria.LABORATORIO, "Glucosa", TRIAJE);
		conRevision(e);
		comoMedico();

		ExtraccionResponse r = service.rechazar(REVISION, " documento ilegible ");

		assertThat(r.estado()).isEqualTo(Extraccion.Estado.RECHAZADA);
		assertThat(r.motivoRechazo()).isEqualTo("documento ilegible");
		assertThat(item.getEstado()).isEqualTo(ExtraccionItem.Estado.DESCARTADO);
		verifyNoInteractions(alergiaService, antecedenteService, laboratorioService);
	}

	@Test
	void unaRevisionCerradaNoSeModifica() {
		Extraccion e = revision();
		e.setEstado(Extraccion.Estado.VALIDADA);
		conRevision(e);

		assertThatThrownBy(() -> service.agregarItem(REVISION, laboratorio("Glucosa", "90")))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("cerrada");
	}

	// ------------------------------------------------------------------------------------------------------------

	private static void comoTriaje() {
		TestSeguridad.autenticarComo(TRIAJE, "triaje1", "TRIAJE");
	}

	private static void comoMedico() {
		TestSeguridad.autenticarComo(MEDICO, "medico1", "MEDICO");
	}

	private void conRevision(Extraccion e) {
		when(repository.findById(REVISION)).thenReturn(Optional.of(e));
	}

	private Extraccion revision() {
		Extraccion e = new Extraccion();
		ReflectionTestUtils.setField(e, "id", REVISION);
		e.setDocumento(documento);
		e.setPaciente(documento.getPaciente());
		e.setCreadoPor(usuario(TRIAJE));
		return e;
	}

	private static ExtraccionItem item(Extraccion e, long id, Categoria categoria, String descripcion, long autor) {
		ExtraccionItem item = new ExtraccionItem();
		ReflectionTestUtils.setField(item, "id", id);
		item.setExtraccion(e);
		item.setCategoria(categoria);
		item.setDescripcion(descripcion);
		item.setCreadoPor(usuario(autor));
		e.getItems().add(item);
		return item;
	}

	private static ItemRequest laboratorio(String examen, String valor) {
		return new ItemRequest(Categoria.LABORATORIO, examen, null, null, null, null, null, null, valor, "mg/dL", null,
				null, null, null);
	}

	private static Usuario usuario(long id) {
		Usuario u = new Usuario();
		ReflectionTestUtils.setField(u, "id", id);
		u.setNombres("Nombre");
		u.setApellidos("Apellido " + id);
		return u;
	}

}
