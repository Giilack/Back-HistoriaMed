package com.historiamed.backend.extraccion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ExtraccionRepository extends JpaRepository<Extraccion, Long> {

	/**
	 * Con {@code estado = RECHAZADA} devuelve la revisión vigente del documento (la pendiente o la validada): hay
	 * como máximo una (índice único {@code uk_extracciones_documento_vigente}).
	 */
	Optional<Extraccion> findByDocumentoIdAndEstadoNot(Long documentoId, Extraccion.Estado estado);

	/** Todas las revisiones del paciente, las más recientes primero. */
	List<Extraccion> findByPacienteIdOrderByCreadoEnDesc(Long pacienteId);

	/** Marca como error las consultas a la IA que quedaron a medias (por ejemplo, por un reinicio del servidor). */
	@Modifying
	@Query("""
			update Extraccion e set e.iaEstado = com.historiamed.backend.extraccion.Extraccion.EstadoIa.ERROR,
			    e.iaError = :mensaje, e.iaTerminadaEn = :ahora
			where e.iaEstado = com.historiamed.backend.extraccion.Extraccion.EstadoIa.PROCESANDO""")
	int marcarIaInterrumpida(String mensaje, Instant ahora);

}
