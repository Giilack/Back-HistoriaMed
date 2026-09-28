package com.historiamed.backend.alergia;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlergiaRepository extends JpaRepository<Alergia, Long> {

	/** Activas primero; dentro de cada grupo, las más recientes primero. */
	@EntityGraph(attributePaths = "registradoPor")
	List<Alergia> findByPacienteIdOrderByActivaDescCreadoEnDesc(Long pacienteId);

	boolean existsByPacienteIdAndSustanciaIgnoreCaseAndActivaTrue(Long pacienteId, String sustancia);

}
