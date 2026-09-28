package com.historiamed.backend.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.paciente.dto.FinanciamientoRequest;
import com.historiamed.backend.paciente.dto.PacienteRequest;
import com.historiamed.backend.paciente.dto.VerificacionSeguroRequest;

@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

	@Mock
	private PacienteRepository repository;

	@Mock
	private AuditoriaService auditoria;

	private PacienteService service;

	@BeforeEach
	void setUp() {
		service = new PacienteService(repository, auditoria);
		TestSeguridad.autenticarComo(7L, "admision1", "ADMISION");
	}

	@AfterEach
	void tearDown() {
		TestSeguridad.limpiar();
	}

	@Test
	void registrarAsignaNumeroHcCorrelativoYNormalizaDatos() {
		when(repository.siguienteNumeroHc()).thenReturn(42L);

		Paciente p = service.registrar(request(TipoDocumento.DNI, "12345678", null));

		assertThat(p.getNumeroHc()).isEqualTo("HC-000042");
		assertThat(p.getNombres()).isEqualTo("Rosa María");
		assertThat(p.getCreadoPor()).isEqualTo(7L);
		// Sin financiamiento indicado queda como PARTICULAR
		assertThat(p.getTipoFinanciamiento()).isEqualTo(TipoFinanciamiento.PARTICULAR);
		assertThat(p.getSeguroEstado()).isNull();
	}

	@Test
	void dniDuplicadoIndicaLaHistoriaExistente() {
		Paciente existente = paciente(5L);
		existente.setNumeroHc("HC-000005");
		when(repository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, "12345678"))
			.thenReturn(Optional.of(existente));

		assertThatThrownBy(() -> service.registrar(request(TipoDocumento.DNI, "12345678", null)))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("HC-000005");
		verify(repository, never()).save(any());
	}

	@Test
	void dniDebeTenerOchoDigitos() {
		assertThatThrownBy(() -> service.registrar(request(TipoDocumento.DNI, "1234", null)))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("8 dígitos");
	}

	@Test
	void pacienteSinDocumentoNoGuardaNumero() {
		when(repository.siguienteNumeroHc()).thenReturn(1L);

		Paciente p = service.registrar(request(TipoDocumento.SIN_DOCUMENTO, "99999999", null));

		assertThat(p.getNumeroDocumento()).isNull();
	}

	@Test
	void fechaDeNacimientoMayorA120AniosSeRechaza() {
		var req = new PacienteRequest(TipoDocumento.SIN_DOCUMENTO, null, "Ana", "Ruiz", null, LocalDate.of(1850, 1, 1),
				Sexo.FEMENINO, null, null, null, null, null, null, null);

		assertThatThrownBy(() -> service.registrar(req)).isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("120");
	}

	@Test
	void registrarConSisQuedaPendienteDeVerificacion() {
		when(repository.siguienteNumeroHc()).thenReturn(3L);
		var sis = new FinanciamientoRequest(TipoFinanciamiento.SIS, "SIS-001", "SIS Gratuito", null);

		Paciente p = service.registrar(request(TipoDocumento.DNI, "12345678", sis));

		assertThat(p.getTipoFinanciamiento()).isEqualTo(TipoFinanciamiento.SIS);
		assertThat(p.getSeguroEstado()).isEqualTo(EstadoSeguro.NO_VERIFICADO);
	}

	@Test
	void seguroPrivadoExigeAseguradora() {
		var privado = new FinanciamientoRequest(TipoFinanciamiento.PRIVADO, "P-1", null, null);

		assertThatThrownBy(() -> service.registrar(request(TipoDocumento.DNI, "12345678", privado)))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("aseguradora");
	}

	@Test
	void pasarAParticularLimpiaElSeguroYPermiteOrientarAlSis() {
		Paciente p = paciente(1L);
		p.setTipoFinanciamiento(TipoFinanciamiento.SIS);
		p.setSeguroNumeroAfiliacion("SIS-001");
		p.setSeguroEstado(EstadoSeguro.INACTIVO);
		when(repository.findById(1L)).thenReturn(Optional.of(p));

		service.cambiarFinanciamiento(1L, new FinanciamientoRequest(TipoFinanciamiento.PARTICULAR, "x", "y", true));

		assertThat(p.getTipoFinanciamiento()).isEqualTo(TipoFinanciamiento.PARTICULAR);
		assertThat(p.getSeguroNumeroAfiliacion()).isNull();
		assertThat(p.getSeguroEstado()).isNull();
		assertThat(p.isOrientadoAfiliacionSis()).isTrue();
	}

	@Test
	void cambiarNumeroDeSeguroAnulaLaVerificacionAnterior() {
		Paciente p = paciente(1L);
		p.setTipoFinanciamiento(TipoFinanciamiento.SIS);
		p.setSeguroNumeroAfiliacion("SIS-001");
		p.setSeguroEstado(EstadoSeguro.ACTIVO);
		when(repository.findById(1L)).thenReturn(Optional.of(p));

		service.cambiarFinanciamiento(1L, new FinanciamientoRequest(TipoFinanciamiento.SIS, "SIS-999", null, null));

		assertThat(p.getSeguroEstado()).isEqualTo(EstadoSeguro.NO_VERIFICADO);
		assertThat(p.getSeguroVerificadoEn()).isNull();
	}

	@Test
	void noSeVerificaSeguroDeUnPacienteParticular() {
		when(repository.findById(1L)).thenReturn(Optional.of(paciente(1L)));

		assertThatThrownBy(() -> service.verificarSeguro(1L, new VerificacionSeguroRequest(EstadoSeguro.ACTIVO)))
			.isInstanceOf(ReglaNegocioException.class)
			.hasMessageContaining("PARTICULAR");
	}

	@Test
	void verificarSeguroRegistraEstadoYFecha() {
		Paciente p = paciente(1L);
		p.setTipoFinanciamiento(TipoFinanciamiento.SIS);
		p.setSeguroEstado(EstadoSeguro.NO_VERIFICADO);
		when(repository.findById(1L)).thenReturn(Optional.of(p));

		service.verificarSeguro(1L, new VerificacionSeguroRequest(EstadoSeguro.ACTIVO));

		assertThat(p.getSeguroEstado()).isEqualTo(EstadoSeguro.ACTIVO);
		assertThat(p.getSeguroVerificadoEn()).isNotNull();
	}

	@Test
	void formatosDeBusquedaYLimpieza() {
		assertThat(PacienteService.formatearHc(12)).isEqualTo("HC-000012");
		assertThat(PacienteService.limpiar("  Rosa    María ")).isEqualTo("Rosa María");
		assertThat(PacienteService.limpiar("   ")).isNull();
	}

	private static PacienteRequest request(TipoDocumento tipo, String numero, FinanciamientoRequest financiamiento) {
		return new PacienteRequest(tipo, numero, "  Rosa   María ", "Quispe", "Mamani", LocalDate.of(1985, 3, 14),
				Sexo.FEMENINO, "987654321", null, "Av. Los Olivos 123", null, null, null, financiamiento);
	}

	private static Paciente paciente(Long id) {
		Paciente p = new Paciente();
		ReflectionTestUtils.setField(p, "id", id);
		p.setNumeroHc("HC-00000" + id);
		p.setTipoDocumento(TipoDocumento.DNI);
		p.setNumeroDocumento("1000000" + id);
		p.setNombres("Juan");
		p.setApellidoPaterno("Pérez");
		p.setFechaNacimiento(LocalDate.of(1980, 1, 1));
		p.setSexo(Sexo.MASCULINO);
		return p;
	}

}
