package com.historiamed.backend.consultorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultorioRepository extends JpaRepository<Consultorio, Long> {

	List<Consultorio> findAllByOrderByNombreAsc();

	List<Consultorio> findByActivoTrueOrderByNombreAsc();

	boolean existsByNombreIgnoreCase(String nombre);

	boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

}
