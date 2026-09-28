package com.historiamed.backend.paciente;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface PacienteRepository extends JpaRepository<Paciente, Long>, JpaSpecificationExecutor<Paciente> {

	Optional<Paciente> findByTipoDocumentoAndNumeroDocumento(TipoDocumento tipo, String numero);

	@Query(value = "SELECT nextval('seq_numero_hc')", nativeQuery = true)
	long siguienteNumeroHc();

}
