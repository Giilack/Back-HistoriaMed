package com.historiamed.backend.documento;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

	@EntityGraph(attributePaths = "subidoPor")
	List<Documento> findByPacienteIdOrderByCreadoEnDesc(Long pacienteId);

}
