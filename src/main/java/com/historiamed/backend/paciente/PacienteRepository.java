package com.historiamed.backend.paciente;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface PacienteRepository extends JpaRepository<Paciente, Long>, JpaSpecificationExecutor<Paciente> {

	/** El número de documento se guarda cifrado: se busca por su huella ({@code CifradoDatos.huella}). */
	Optional<Paciente> findByTipoDocumentoAndNumeroDocumentoHuella(TipoDocumento tipo, String huella);

	@Query(value = "SELECT nextval('seq_numero_hc')", nativeQuery = true)
	long siguienteNumeroHc();

}
