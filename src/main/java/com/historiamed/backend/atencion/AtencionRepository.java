package com.historiamed.backend.atencion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.historiamed.backend.common.dto.Conteo;

public interface AtencionRepository extends JpaRepository<Atencion, Long> {

	Optional<Atencion> findByCitaId(Long citaId);

	List<Atencion> findByPacienteIdOrderByInicioEnDesc(Long pacienteId, Pageable pageable);

	/** Atenciones firmadas cuyo control sugerido cae en esa fecha o después, de la más próxima a la más lejana. */
	@EntityGraph(attributePaths = { "paciente", "medico", "cita.consultorio" })
	List<Atencion> findByEstadoAndControlFechaGreaterThanEqualOrderByControlFecha(EstadoAtencion estado,
			LocalDate desde);

	/** Diagnósticos principales más frecuentes en atenciones cerradas; el tamaño de la página limita el ranking. */
	@Query("""
			select new com.historiamed.backend.common.dto.Conteo(d.cie10.codigo, d.cie10.descripcion, count(a))
			from Atencion a join a.diagnosticos d join a.cita c
			where d.principal = true and a.estado = com.historiamed.backend.atencion.EstadoAtencion.CERRADA
			  and c.fecha between :desde and :hasta
			group by d.cie10.codigo, d.cie10.descripcion order by count(a) desc, d.cie10.codigo""")
	List<Conteo> contarDiagnosticosPrincipales(LocalDate desde, LocalDate hasta, Pageable pagina);

}
