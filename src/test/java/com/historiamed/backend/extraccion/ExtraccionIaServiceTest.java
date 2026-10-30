package com.historiamed.backend.extraccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.historiamed.backend.TestSeguridad;
import com.historiamed.backend.alergia.AlergiaService;
import com.historiamed.backend.alergia.GravedadAlergia;
import com.historiamed.backend.antecedente.AntecedenteService;
import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.catalogo.CatalogoService;
import com.historiamed.backend.catalogo.Cie10;
import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.documento.Documento;
import com.historiamed.backend.documento.DocumentoService;
import com.historiamed.backend.extraccion.ExtraccionItem.Categoria;
import com.historiamed.backend.ia.ErrorIaException;
import com.historiamed.backend.ia.ProveedorIA;
import com.historiamed.backend.laboratorio.LaboratorioService;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.usuario.Usuario;
import com.historiamed.backend.usuario.UsuarioService;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ExtraccionIaServiceTest {

	private static final long REVISION = 20L;

	private static final long TRIAJE = 5L;

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

	@Mock
	private ProveedorIA proveedor;

	@Mock
	private PlatformTransactionManager transacciones;

	private ExtraccionIaService service;

	private Extraccion revision;

	@BeforeEach
	void setUp() {
		ExtraccionService extraccionService = new ExtraccionService(repository, documentoService, catalogoService,
				alergiaService, antecedenteService, laboratorioService, usuarioService, auditoria);
		service = new ExtraccionIaService(repository, extraccionService, documentoService, catalogoService,
				usuarioService, auditoria, proveedor, JsonMapper.builder().build(), new SyncTaskExecutor(),
				transacciones);

		Paciente paciente = new Paciente();
		ReflectionTestUtils.setField(paciente, "id", 3L);
		Documento documento = new Documento();
		ReflectionTestUtils.setField(documento, "id", 9L);
		documento.setPaciente(paciente);
		documento.setContentType("application/pdf");
		revision = new Extraccion();
		ReflectionTestUtils.setField(revision, "id", REVISION);
		revision.setDocumento(documento);
		revision.setPaciente(paciente);
		revision.setCreadoPor(usuario());

		lenient().when(repository.findById(REVISION)).thenReturn(Optional.of(revision));
		lenient().when(usuarioService.obtener(TRIAJE)).thenReturn(usuario());
		lenient().when(proveedor.disponible()).thenReturn(true);
		lenient().when(proveedor.modelo()).thenReturn("gemini-prueba");
		TestSeguridad.autenticarComo(TRIAJE, "triaje1", "TRIAJE");
		// Sin Spring, se activa a mano el registro de acciones "al confirmar la transacción"
		TransactionSynchronizationManager.initSynchronization();
	}

	@AfterEach
	void tearDown() {
		TransactionSynchronizationManager.clearSynchronization();
		TestSeguridad.limpiar();
	}

	// ---------------------------------------------------------------------------------------------- solicitar

	@Test
	void solicitarDejaLaRevisionProcesandoYLoAudita() {
		var r = service.solicitar(REVISION);

		assertThat(r.iaEstado()).isEqualTo(Extraccion.EstadoIa.PROCESANDO);
		assertThat(r.iaModelo()).isEqualTo("gemini-prueba");
		assertThat(revision.getIaSolicitadaEn()).isNotNull();
		verify(auditoria).registrarSobrePaciente(eq(AccionAuditoria.CONSULTA_IA), anyString(), eq(REVISION), eq(3L),
				anyString());
		// La consulta se programa para cuando se confirme la transacción
		assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
	}

	@Test
	void sinIaConfiguradaNoSePuedeSolicitar() {
		when(proveedor.disponible()).thenReturn(false);

		assertThatThrownBy(() -> service.solicitar(REVISION)).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("no está configurada");
	}

	@Test
	void laIaSoloSeUsaEnUnaRevisionVacia() {
		ExtraccionItem item = new ExtraccionItem();
		item.setCategoria(Categoria.OTRO);
		item.setDescripcion("Nota escrita a mano");
		revision.getItems().add(item);

		assertThatThrownBy(() -> service.solicitar(REVISION)).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("vacía");
		verify(auditoria, never()).registrarSobrePaciente(any(), any(), any(), any(), any());
	}

	@Test
	void noSePideDosVecesNiEnUnaRevisionCerrada() {
		revision.setIaEstado(Extraccion.EstadoIa.PROCESANDO);
		assertThatThrownBy(() -> service.solicitar(REVISION)).hasMessageContaining("ya está leyendo");

		revision.setIaEstado(null);
		revision.setEstado(Extraccion.Estado.VALIDADA);
		assertThatThrownBy(() -> service.solicitar(REVISION)).hasMessageContaining("cerrada");
	}

	@Test
	void mientrasLaIaTrabajaNoSeEditaLaRevisionAMano() {
		revision.setIaEstado(Extraccion.EstadoIa.PROCESANDO);
		ExtraccionService extraccionService = new ExtraccionService(repository, documentoService, catalogoService,
				alergiaService, antecedenteService, laboratorioService, usuarioService, auditoria);

		assertThatThrownBy(() -> extraccionService.validar(REVISION, java.util.Set.of()))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("IA");
	}

	// ---------------------------------------------------------------------------------------------- procesar

	@Test
	void laRespuestaDeLaIaQuedaComoDatosPropuestos() {
		revision.setIaEstado(Extraccion.EstadoIa.PROCESANDO);
		Cie10 cie = new Cie10();
		ReflectionTestUtils.setField(cie, "codigo", "E11.9");
		ReflectionTestUtils.setField(cie, "descripcion", "Diabetes mellitus no insulinodependiente");
		when(catalogoService.obtenerCie10("E11.9")).thenReturn(cie);
		responde("""
				{"datos": [
				  {"categoria": "LABORATORIO", "descripcion": "Hemoglobina", "valor": "11.2", "unidad": "g/dL",
				   "rangoReferencia": "12 - 16", "fecha": "2026-09-15", "fragmentoOrigen": "HEMOGLOBINA 11.2 g/dL",
				   "pagina": 1},
				  {"categoria": "DIAGNOSTICO", "descripcion": "Diabetes", "cieCodigo": "E11.9"},
				  {"categoria": "ALERGIA", "descripcion": "Penicilina", "tipoAlergia": "MEDICAMENTO",
				   "gravedad": "severa", "detalle": "urticaria"}
				]}""");

		service.procesar(REVISION, TRIAJE);

		assertThat(revision.getIaEstado()).isEqualTo(Extraccion.EstadoIa.COMPLETADA);
		assertThat(revision.getOrigen()).isEqualTo(Extraccion.Origen.IA);
		assertThat(revision.getIaTerminadaEn()).isNotNull();
		assertThat(revision.getItems()).hasSize(3).allSatisfy(i -> {
			assertThat(i.isPropuestoPorIa()).isTrue();
			assertThat(i.getEstado()).isEqualTo(ExtraccionItem.Estado.PROPUESTO);
			assertThat(i.getCreadoPor().getId()).isEqualTo(TRIAJE);
		});
		ExtraccionItem lab = revision.getItems().get(0);
		assertThat(lab.getValor()).isEqualTo("11.2");
		assertThat(lab.getFecha()).isEqualTo(LocalDate.of(2026, 9, 15));
		assertThat(lab.getPagina()).isEqualTo(1);
		// El diagnóstico toma la descripción del catálogo, no la de la IA
		assertThat(revision.getItems().get(1).getDescripcion()).isEqualTo("Diabetes mellitus no insulinodependiente");
		// Las mayúsculas no importan en los valores fijos
		assertThat(revision.getItems().get(2).getGravedad()).isEqualTo(GravedadAlergia.SEVERA);
	}

	@Test
	void unDatoQueNoCumpleLasReglasSeConservaComoOtraInformacion() {
		revision.setIaEstado(Extraccion.EstadoIa.PROCESANDO);
		when(catalogoService.obtenerCie10("Z99.9")).thenThrow(new RecursoNoEncontradoException("CIE-10", "Z99.9"));
		responde("""
				{"datos": [
				  {"categoria": "DIAGNOSTICO", "descripcion": "Dependencia de máquina", "cieCodigo": "Z99.9"},
				  {"categoria": "LABORATORIO", "descripcion": "Glucosa"},
				  {"categoria": "INVENTADA", "descripcion": "x"},
				  {"categoria": "OTRO", "descripcion": "Control", "fecha": "2999-01-01", "pagina": 0}
				]}""");

		service.procesar(REVISION, TRIAJE);

		List<ExtraccionItem> items = revision.getItems();
		// La categoría desconocida se descarta; el resto se conserva
		assertThat(items).hasSize(3);
		assertThat(items.get(0).getCategoria()).isEqualTo(Categoria.OTRO);
		assertThat(items.get(0).getDescripcion()).contains("Dependencia de máquina").contains("Z99.9");
		// Laboratorio sin resultado: no cumple, pasa a OTRO
		assertThat(items.get(1).getCategoria()).isEqualTo(Categoria.OTRO);
		// Fecha futura y página 0 se descartan
		assertThat(items.get(2).getFecha()).isNull();
		assertThat(items.get(2).getPagina()).isNull();
	}

	@Test
	void siLaIaFallaQuedaElErrorParaReintentar() {
		revision.setIaEstado(Extraccion.EstadoIa.PROCESANDO);
		when(documentoService.leerContenido(any())).thenReturn(new byte[] { 1 });
		when(proveedor.extraerJson(any(), any(), any(), any()))
			.thenThrow(new ErrorIaException("Se alcanzó el límite de uso gratuito de la IA"));

		service.procesar(REVISION, TRIAJE);

		assertThat(revision.getIaEstado()).isEqualTo(Extraccion.EstadoIa.ERROR);
		assertThat(revision.getIaError()).contains("límite");
		assertThat(revision.getItems()).isEmpty();
	}

	@Test
	void unaRespuestaQueNoEsJsonQuedaComoError() {
		revision.setIaEstado(Extraccion.EstadoIa.PROCESANDO);
		responde("esto no es JSON");

		service.procesar(REVISION, TRIAJE);

		assertThat(revision.getIaEstado()).isEqualTo(Extraccion.EstadoIa.ERROR);
		assertThat(revision.getIaError()).contains("formato");
	}

	@Test
	void lasInstruccionesIncluyenLosCatalogos() {
		Cie10 cie = new Cie10();
		ReflectionTestUtils.setField(cie, "codigo", "J00");
		ReflectionTestUtils.setField(cie, "descripcion", "Rinofaringitis aguda");
		when(catalogoService.todosCie10()).thenReturn(List.of(cie));
		when(catalogoService.medicamentosActivos()).thenReturn(List.of());

		assertThat(service.instrucciones()).contains("J00 — Rinofaringitis aguda").contains("No copies datos que identifiquen");
	}

	// ------------------------------------------------------------------------------------------------------------

	private void responde(String json) {
		when(documentoService.leerContenido(any())).thenReturn(new byte[] { 1 });
		when(proveedor.extraerJson(any(), eq("application/pdf"), any(), any())).thenReturn(json);
	}

	private static Usuario usuario() {
		Usuario u = new Usuario();
		ReflectionTestUtils.setField(u, "id", TRIAJE);
		u.setNombres("Pedro");
		u.setApellidos("Salas");
		return u;
	}

}
