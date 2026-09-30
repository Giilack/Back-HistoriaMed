package com.historiamed.backend.laboratorio;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ResultadoLaboratorioRepository extends JpaRepository<ResultadoLaboratorio, Long> {

	/** Activos primero; los más recientes primero (por fecha del examen y, si falta, por registro). */
	@EntityGraph(attributePaths = "registradoPor")
	@Query("""
			SELECT r FROM ResultadoLaboratorio r
			WHERE r.paciente.id = :pacienteId
			ORDER BY r.activo DESC, r.fecha DESC NULLS LAST, r.creadoEn DESC""")
	List<ResultadoLaboratorio> delPaciente(Long pacienteId);

}
