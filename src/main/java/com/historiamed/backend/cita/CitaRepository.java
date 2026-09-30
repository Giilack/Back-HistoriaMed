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

}
