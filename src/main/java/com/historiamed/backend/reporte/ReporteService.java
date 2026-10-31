package com.historiamed.backend.reporte;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.atencion.AtencionService;
import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.cita.EstadisticasCitaService;
import com.historiamed.backend.cita.dto.PromediosCita;
import com.historiamed.backend.common.dto.Conteo;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.util.Tiempo;
import com.historiamed.backend.paciente.PacienteService;

import lombok.RequiredArgsConstructor;

/**
 * Indicadores de gestión. Cada dato lo entrega el servicio de su módulo (repositorios Spring Data con JPQL y
 * parámetros enlazados): este servicio no conoce tablas ni escribe SQL. El período se filtra por la fecha de la cita.
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

	private static final long MAXIMO_DIAS = 366;

	private static final int TOP_DIAGNOSTICOS = 10;

	private static final double SEGUNDOS_POR_MINUTO = 60.0;

	private final EstadisticasCitaService citas;

	private final AtencionService atenciones;

	private final PacienteService pacientes;

	private final AuditoriaService auditoria;

	/** No es de solo lectura: registra en la auditoría que se consultó el reporte. */
	@Transactional
	public ReporteResponse generar(LocalDate desdeParam, LocalDate hastaParam) {
		LocalDate hasta = hastaParam != null ? hastaParam : Tiempo.hoy();
		LocalDate desde = desdeParam != null ? desdeParam : hasta.minusDays(29);
		if (desde.isAfter(hasta)) {
			throw new ReglaNegocioException("La fecha inicial es posterior a la final");
		}
		if (ChronoUnit.DAYS.between(desde, hasta) >= MAXIMO_DIAS) {
			throw new ReglaNegocioException("El período máximo es de un año");
		}

		List<Conteo> porEstado = citas.porEstado(desde, hasta);
		ReporteResponse.Totales totales = totales(porEstado, pacientes.contarRegistrados(desde, hasta));

		auditoria.registrar(AccionAuditoria.VER, "REPORTE", null, desde + " a " + hasta);
		return new ReporteResponse(desde, hasta, totales, tiempos(citas.promedios(desde, hasta)), porEstado,
				citas.atendidasPorDia(desde, hasta), citas.atendidasPorMedico(desde, hasta),
				citas.atendidasPorConsultorio(desde, hasta),
				atenciones.diagnosticosFrecuentes(desde, hasta, TOP_DIAGNOSTICOS), citas.porPrioridad(desde, hasta),
				citas.pacientesPorFinanciamiento(desde, hasta));
	}

	private static ReporteResponse.TiemposEspera tiempos(PromediosCita p) {
		return new ReporteResponse.TiemposEspera(minutos(p.llegadaATriaje()), minutos(p.triajeAConsulta()),
				minutos(p.duracionConsulta()));
	}

	private static ReporteResponse.Totales totales(List<Conteo> porEstado, long pacientesNuevos) {
		long citas = porEstado.stream().mapToLong(Conteo::total).sum();
		long atendidas = total(porEstado, "ATENDIDO");
		long noSePresento = total(porEstado, "NO_SE_PRESENTO");
		long canceladas = total(porEstado, "CANCELADA");
		long base = citas - canceladas;
		Double tasa = base == 0 ? null : redondear(100.0 * noSePresento / base);
		return new ReporteResponse.Totales(citas, atendidas, noSePresento, canceladas, tasa, pacientesNuevos);
	}

	private static long total(List<Conteo> conteos, String codigo) {
		return conteos.stream().filter(c -> codigo.equals(c.codigo())).mapToLong(Conteo::total).sum();
	}

	private static Double minutos(Double segundos) {
		return segundos == null ? null : redondear(segundos / SEGUNDOS_POR_MINUTO);
	}

	/** Un decimal. */
	private static Double redondear(Double valor) {
		return valor == null ? null : Math.round(valor * 10) / 10.0;
	}

}
