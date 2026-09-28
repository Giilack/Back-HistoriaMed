package com.historiamed.backend.atencion;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AtencionRepository extends JpaRepository<Atencion, Long> {

	Optional<Atencion> findByCitaId(Long citaId);

	List<Atencion> findByPacienteIdOrderByInicioEnDesc(Long pacienteId, Pageable pageable);

}
