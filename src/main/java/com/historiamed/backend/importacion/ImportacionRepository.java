package com.historiamed.backend.importacion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ImportacionRepository extends JpaRepository<Importacion, Long> {

	Optional<Importacion> findFirstByEstadoOrderById(Importacion.Estado estado);

	boolean existsBySha256AndEstadoNot(String sha256, Importacion.Estado estado);

	long countByEstadoIn(Collection<Importacion.Estado> estados);

	@EntityGraph(attributePaths = "subidoPor")
	List<Importacion> findTop200ByOrderByIdDesc();

	@Modifying
	@Query("update Importacion i set i.estado = :cola where i.estado = :procesando")
	int devolverACola(Importacion.Estado procesando, Importacion.Estado cola);

}
