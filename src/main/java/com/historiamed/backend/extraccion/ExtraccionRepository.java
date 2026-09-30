package com.historiamed.backend.extraccion;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtraccionRepository extends JpaRepository<Extraccion, Long> {

	/**
	 * Con {@code estado = RECHAZADA} devuelve la revisión vigente del documento (la pendiente o la validada): hay
	 * como máximo una (índice único {@code uk_extracciones_documento_vigente}).
	 */
	Optional<Extraccion> findByDocumentoIdAndEstadoNot(Long documentoId, Extraccion.Estado estado);

	/** Todas las revisiones del paciente, las más recientes primero. */
	List<Extraccion> findByPacienteIdOrderByCreadoEnDesc(Long pacienteId);

}
