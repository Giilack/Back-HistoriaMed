package com.historiamed.backend.atencion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AtencionRepository extends JpaRepository<Atencion, Long> {

	Optional<Atencion> findByCitaId(Long citaId);

	List<Atencion> findByPacienteIdOrderByInicioEnDesc(Long pacienteId, Pageable pageable);

	/** Atenciones firmadas cuyo control sugerido cae en esa fecha o después, de la más próxima a la más lejana. */
	@EntityGraph(attributePaths = { "paciente", "medico", "cita.consultorio" })
	List<Atencion> findByEstadoAndControlFechaGreaterThanEqualOrderByControlFecha(EstadoAtencion estado,
			LocalDate desde);

}
