package com.historiamed.backend.antecedente;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AntecedenteRepository extends JpaRepository<Antecedente, Long> {

	/** Activos primero; dentro de cada grupo, los más recientes primero. */
	@EntityGraph(attributePaths = { "registradoPor", "cie10", "medicamento" })
	List<Antecedente> findByPacienteIdOrderByActivoDescCreadoEnDesc(Long pacienteId);

}
