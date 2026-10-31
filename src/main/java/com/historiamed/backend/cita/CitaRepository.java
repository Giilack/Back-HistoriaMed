package com.historiamed.backend.cita;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.historiamed.backend.cita.dto.PromediosCita;
import com.historiamed.backend.common.dto.Conteo;
import com.historiamed.backend.common.dto.ConteoPorFecha;

public interface CitaRepository extends JpaRepository<Cita, Long>, JpaSpecificationExecutor<Cita> {

	/** Carga paciente, médico y consultorio en la misma consulta (evita una consulta extra por cita). */
	@Override
	@EntityGraph(attributePaths = { "paciente", "medico", "consultorio" })
	List<Cita> findAll(Specification<Cita> spec, Sort sort);

	@Query("""
			select count(c) > 0 from Cita c
			where c.paciente.id = :pacienteId and c.fecha = :fecha and c.consultorio.id = :consultorioId
			  and c.estado in :estados and c.id <> :excluirId""")
	boolean pacienteTieneTurnoActivo(Long pacienteId, LocalDate fecha, Long consultorioId,
			Collection<EstadoCita> estados, Long excluirId);

	@Query("""
			select count(c) > 0 from Cita c
			where c.medico.id = :medicoId and c.fecha = :fecha and c.hora = :hora
			  and c.estado in :estados and c.id <> :excluirId""")
	boolean medicoTieneCitaALaHora(Long medicoId, LocalDate fecha, LocalTime hora, Collection<EstadoCita> estados,
			Long excluirId);

	@Query("""
			select count(c) > 0 from Cita c
			where c.paciente.id = :pacienteId and c.fecha > :despuesDe and c.estado in :estados""")
	boolean pacienteTieneCitaPosterior(Long pacienteId, LocalDate despuesDe, Collection<EstadoCita> estados);

	@Query("select coalesce(max(c.numeroTurno), 0) from Cita c where c.fecha = :fecha and c.consultorio.id = :consultorioId")
	int ultimoTurno(LocalDate fecha, Long consultorioId);

	// Indicadores para los reportes: JPQL con parámetros enlazados, nunca texto concatenado

	@Query("""
			select new com.historiamed.backend.common.dto.Conteo(cast(c.estado as string), cast(c.estado as string), count(c))
			from Cita c where c.fecha between :desde and :hasta
			group by c.estado order by count(c) desc""")
	List<Conteo> contarPorEstado(LocalDate desde, LocalDate hasta);

	@Query("""
			select new com.historiamed.backend.cita.dto.PromediosCita(
			    avg(extract(epoch from c.triajeEn) - extract(epoch from c.llegadaEn)),
			    avg(extract(epoch from c.consultaInicioEn) - extract(epoch from c.triajeEn)),
			    avg(extract(epoch from c.atendidoEn) - extract(epoch from c.consultaInicioEn)))
			from Cita c where c.fecha between :desde and :hasta""")
	PromediosCita promediarTiempos(LocalDate desde, LocalDate hasta);

	@Query("""
			select new com.historiamed.backend.common.dto.ConteoPorFecha(c.fecha, count(c))
			from Cita c
			where c.estado = com.historiamed.backend.cita.EstadoCita.ATENDIDO and c.fecha between :desde and :hasta
			group by c.fecha order by c.fecha""")
	List<ConteoPorFecha> contarAtendidasPorDia(LocalDate desde, LocalDate hasta);

	@Query("""
			select new com.historiamed.backend.common.dto.Conteo(cast(m.id as string),
			    concat(m.apellidos, ', ', m.nombres), count(c))
			from Cita c join c.medico m
			where c.estado = com.historiamed.backend.cita.EstadoCita.ATENDIDO and c.fecha between :desde and :hasta
			group by m.id, m.apellidos, m.nombres order by count(c) desc""")
	List<Conteo> contarAtendidasPorMedico(LocalDate desde, LocalDate hasta);

	@Query("""
			select new com.historiamed.backend.common.dto.Conteo(cast(co.id as string),
			    concat(co.nombre, ' · ', co.especialidad), count(c))
			from Cita c join c.consultorio co
			where c.estado = com.historiamed.backend.cita.EstadoCita.ATENDIDO and c.fecha between :desde and :hasta
			group by co.id, co.nombre, co.especialidad order by count(c) desc""")
	List<Conteo> contarAtendidasPorConsultorio(LocalDate desde, LocalDate hasta);

	@Query("""
			select new com.historiamed.backend.common.dto.Conteo(cast(c.prioridad as string), cast(c.prioridad as string), count(c))
			from Cita c where c.prioridad is not null and c.fecha between :desde and :hasta
			group by c.prioridad order by count(c) desc""")
	List<Conteo> contarPorPrioridad(LocalDate desde, LocalDate hasta);

	@Query("""
			select new com.historiamed.backend.common.dto.Conteo(cast(p.tipoFinanciamiento as string),
			    cast(p.tipoFinanciamiento as string), count(distinct p.id))
			from Cita c join c.paciente p
			where c.estado = com.historiamed.backend.cita.EstadoCita.ATENDIDO and c.fecha between :desde and :hasta
			group by p.tipoFinanciamiento order by count(distinct p.id) desc""")
	List<Conteo> contarPacientesPorFinanciamiento(LocalDate desde, LocalDate hasta);

}
