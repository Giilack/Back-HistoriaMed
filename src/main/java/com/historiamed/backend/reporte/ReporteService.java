package com.historiamed.backend.reporte;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.auditoria.AccionAuditoria;
import com.historiamed.backend.auditoria.AuditoriaService;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.util.Tiempo;
import com.historiamed.backend.reporte.ReporteResponse.Conteo;
import com.historiamed.backend.reporte.ReporteResponse.ConteoPorFecha;

import lombok.RequiredArgsConstructor;

/**
 * Indicadores de gestión con consultas de agregación (SQL directo: es lo natural para contar y promediar).
 * El período se filtra por la fecha de la cita.
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

	private static final long MAXIMO_DIAS = 366;

	private static final int TOP_DIAGNOSTICOS = 10;

	private final NamedParameterJdbcTemplate jdbc;

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
		var p = new MapSqlParameterSource().addValue("desde", desde).addValue("hasta", hasta);

		List<Conteo> porEstado = conteos("""
				SELECT estado AS codigo, estado AS etiqueta, count(*) AS total
				FROM citas WHERE fecha BETWEEN :desde AND :hasta
				GROUP BY estado ORDER BY total DESC""", p);

		ReporteResponse.Totales totales = totales(porEstado, jdbc.queryForObject("""
				SELECT count(*) FROM pacientes
				WHERE (creado_en AT TIME ZONE 'America/Lima')::date BETWEEN :desde AND :hasta""", p, Long.class));

		ReporteResponse.TiemposEspera tiempos = jdbc.queryForObject("""
				SELECT (avg(extract(epoch FROM triaje_en - llegada_en)) / 60)::float8           AS llegada_triaje,
				       (avg(extract(epoch FROM consulta_inicio_en - triaje_en)) / 60)::float8   AS triaje_consulta,
				       (avg(extract(epoch FROM atendido_en - consulta_inicio_en)) / 60)::float8 AS duracion
				FROM citas WHERE fecha BETWEEN :desde AND :hasta""", p,
				(rs, i) -> new ReporteResponse.TiemposEspera(redondear(rs.getObject("llegada_triaje", Double.class)),
						redondear(rs.getObject("triaje_consulta", Double.class)),
						redondear(rs.getObject("duracion", Double.class))));

		List<ConteoPorFecha> porDia = jdbc.query("""
				SELECT fecha, count(*) AS total FROM citas
				WHERE estado = 'ATENDIDO' AND fecha BETWEEN :desde AND :hasta
				GROUP BY fecha ORDER BY fecha""", p,
				(rs, i) -> new ConteoPorFecha(rs.getObject("fecha", LocalDate.class), rs.getLong("total")));

		List<Conteo> porMedico = conteos("""
				SELECT u.id::text AS codigo, u.apellidos || ', ' || u.nombres AS etiqueta, count(*) AS total
				FROM citas c JOIN usuarios u ON u.id = c.medico_id
				WHERE c.estado = 'ATENDIDO' AND c.fecha BETWEEN :desde AND :hasta
				GROUP BY u.id, u.apellidos, u.nombres ORDER BY total DESC""", p);

		List<Conteo> porConsultorio = conteos("""
				SELECT co.id::text AS codigo, co.nombre || ' · ' || co.especialidad AS etiqueta, count(*) AS total
				FROM citas c JOIN consultorios co ON co.id = c.consultorio_id
				WHERE c.estado = 'ATENDIDO' AND c.fecha BETWEEN :desde AND :hasta
				GROUP BY co.id, co.nombre, co.especialidad ORDER BY total DESC""", p);

		List<Conteo> diagnosticos = conteos("""
				SELECT d.cie10_codigo AS codigo, x.descripcion AS etiqueta, count(*) AS total
				FROM atencion_diagnosticos d
				JOIN atenciones a ON a.id = d.atencion_id
				JOIN citas c ON c.id = a.cita_id
				JOIN cie10 x ON x.codigo = d.cie10_codigo
				WHERE d.principal AND a.estado = 'CERRADA' AND c.fecha BETWEEN :desde AND :hasta
				GROUP BY d.cie10_codigo, x.descripcion ORDER BY total DESC, codigo
				LIMIT %d""".formatted(TOP_DIAGNOSTICOS), p);

		List<Conteo> prioridades = conteos("""
				SELECT prioridad AS codigo, prioridad AS etiqueta, count(*) AS total
				FROM citas WHERE prioridad IS NOT NULL AND fecha BETWEEN :desde AND :hasta
				GROUP BY prioridad ORDER BY total DESC""", p);

		List<Conteo> financiamiento = conteos("""
				SELECT p.tipo_financiamiento AS codigo, p.tipo_financiamiento AS etiqueta,
				       count(DISTINCT p.id) AS total
				FROM citas c JOIN pacientes p ON p.id = c.paciente_id
				WHERE c.estado = 'ATENDIDO' AND c.fecha BETWEEN :desde AND :hasta
				GROUP BY p.tipo_financiamiento ORDER BY total DESC""", p);

		auditoria.registrar(AccionAuditoria.VER, "REPORTE", null, desde + " a " + hasta);
		return new ReporteResponse(desde, hasta, totales, tiempos, porEstado, porDia, porMedico, porConsultorio,
				diagnosticos, prioridades, financiamiento);
	}

	private List<Conteo> conteos(String sql, MapSqlParameterSource p) {
		return jdbc.query(sql, p,
				(rs, i) -> new Conteo(rs.getString("codigo"), rs.getString("etiqueta"), rs.getLong("total")));
	}

	private static ReporteResponse.Totales totales(List<Conteo> porEstado, Long pacientesNuevos) {
		long citas = porEstado.stream().mapToLong(Conteo::total).sum();
		long atendidas = total(porEstado, "ATENDIDO");
		long noSePresento = total(porEstado, "NO_SE_PRESENTO");
		long canceladas = total(porEstado, "CANCELADA");
		long base = citas - canceladas;
		Double tasa = base == 0 ? null : redondear(100.0 * noSePresento / base);
		return new ReporteResponse.Totales(citas, atendidas, noSePresento, canceladas, tasa,
				pacientesNuevos == null ? 0 : pacientesNuevos);
	}

	private static long total(List<Conteo> conteos, String codigo) {
		return conteos.stream().filter(c -> codigo.equals(c.codigo())).mapToLong(Conteo::total).sum();
	}

	/** Un decimal. */
	private static Double redondear(Double valor) {
		return valor == null ? null : Math.round(valor * 10) / 10.0;
	}

}
