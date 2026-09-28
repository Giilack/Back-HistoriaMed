package com.historiamed.backend.reporte;

import java.time.LocalDate;
import java.util.List;

/**
 * Indicadores agregados de un período. Solo cifras totales: ningún dato permite identificar a un paciente, por
 * eso los puede ver el ADMIN (plan.md, principio P1).
 */
public record ReporteResponse(LocalDate desde, LocalDate hasta, Totales totales, TiemposEspera tiemposEspera,
		List<Conteo> citasPorEstado, List<ConteoPorFecha> atencionesPorDia, List<Conteo> atencionesPorMedico,
		List<Conteo> atencionesPorConsultorio, List<Conteo> diagnosticosFrecuentes, List<Conteo> prioridades,
		List<Conteo> pacientesPorFinanciamiento) {

	/**
	 * @param tasaInasistencia porcentaje de citas "no se presentó" sobre las citas no canceladas (null si no hay)
	 */
	public record Totales(long citas, long atendidas, long noSePresento, long canceladas, Double tasaInasistencia,
			long pacientesNuevos) {
	}

	/** Promedios en minutos; null si no hay datos en el período. */
	public record TiemposEspera(Double llegadaATriaje, Double triajeAConsulta, Double duracionConsulta) {
	}

	/** @param etiqueta nombre legible; @param codigo valor técnico (por ejemplo, el código CIE-10) */
	public record Conteo(String codigo, String etiqueta, long total) {
	}

	public record ConteoPorFecha(LocalDate fecha, long total) {
	}

}
