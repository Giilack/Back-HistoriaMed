package com.historiamed.backend.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.historiamed.backend.TestcontainersConfiguration;
import com.historiamed.backend.documento.AlmacenamientoService;

/**
 * Observación del Avance 2: los reportes y los archivos pasan por repositorios JPA. Se ejecutan contra PostgreSQL real
 * con los datos de demostración, porque el JPQL (agrupaciones y promedios de tiempos) solo se valida en la base.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = { "historiamed.demo.habilitada=true", "historiamed.demo.password=Demo2026prueba",
		"historiamed.almacenamiento.tipo=bd" })
class ReporteAccesoDatosTest {

	private static final LocalDate HASTA = LocalDate.now();

	private static final LocalDate DESDE = HASTA.minusDays(90);

	@Autowired
	private ReporteService reportes;

	@Autowired
	private AlmacenamientoService almacenamiento;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@WithMockUser(roles = "ADMIN")
	void losIndicadoresCoincidenConLoQueHayEnLaBase() {
		ReporteResponse r = reportes.generar(DESDE, HASTA);

		Long atendidas = jdbc.queryForObject("SELECT count(*) FROM citas WHERE estado = 'ATENDIDO' AND fecha BETWEEN ? AND ?",
				Long.class, DESDE, HASTA);
		assertThat(atendidas).isPositive();
		assertThat(r.totales().atendidas()).isEqualTo(atendidas);
		assertThat(r.atencionesPorDia().stream().mapToLong(c -> c.total()).sum()).isEqualTo(atendidas);
		assertThat(r.atencionesPorMedico().stream().mapToLong(c -> c.total()).sum()).isEqualTo(atendidas);
		assertThat(r.atencionesPorConsultorio().stream().mapToLong(c -> c.total()).sum()).isEqualTo(atendidas);
		assertThat(r.diagnosticosFrecuentes()).isNotEmpty().hasSizeLessThanOrEqualTo(10);
		assertThat(r.diagnosticosFrecuentes().get(0).total())
			.isGreaterThanOrEqualTo(r.diagnosticosFrecuentes().get(r.diagnosticosFrecuentes().size() - 1).total());
		assertThat(r.pacientesPorFinanciamiento()).isNotEmpty();
		assertThat(r.prioridades()).isNotEmpty();
		assertThat(r.tiemposEspera().llegadaATriaje()).isPositive();
		assertThat(r.totales().pacientesNuevos()).isPositive();
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void unPeriodoSinDatosDevuelveCerosYNoFalla() {
		ReporteResponse r = reportes.generar(LocalDate.of(2001, 1, 1), LocalDate.of(2001, 1, 31));

		assertThat(r.totales().citas()).isZero();
		assertThat(r.totales().tasaInasistencia()).isNull();
		assertThat(r.tiemposEspera().llegadaATriaje()).isNull();
	}

	@Test
	void elArchivoGuardadoSeRecuperaIgualYLaBaseImpideCambiarlo() throws Exception {
		byte[] contenido = "%PDF-1.4 ficticio".getBytes(StandardCharsets.US_ASCII);

		String clave = almacenamiento.guardar(contenido, "pdf");

		assertThat(almacenamiento.obtener(clave).getContentAsByteArray()).isEqualTo(contenido);
		assertThatThrownBy(() -> jdbc.update("DELETE FROM archivos WHERE clave = ?", clave))
			.isInstanceOf(DataAccessException.class);
	}

}
