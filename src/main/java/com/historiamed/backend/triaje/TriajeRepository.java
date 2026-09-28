package com.historiamed.backend.triaje;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriajeRepository extends JpaRepository<Triaje, Long> {

	boolean existsByCitaId(Long citaId);

	@EntityGraph(attributePaths = { "registradoPor", "alertas" })
	Optional<Triaje> findByCitaId(Long citaId);

	@EntityGraph(attributePaths = { "registradoPor", "alertas" })
	List<Triaje> findByPacienteIdOrderByFechaHoraDesc(Long pacienteId, Pageable pageable);

}
