package com.historiamed.backend.catalogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicamentoRepository extends JpaRepository<Medicamento, Long> {

	List<Medicamento> findByActivoTrueOrderByNombreAscConcentracionAsc();

}
