package com.historiamed.backend.importacion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ImportacionRepository extends JpaRepository<Importacion, Long> {

	Optional<Importacion> findFirstByEstadoOrderById(Importacion.Estado estado);

	boolean existsBySha256AndEstadoNotIn(String sha256, Collection<Importacion.Estado> estados);

	long countByPacienteIdAndEstado(Long pacienteId, Importacion.Estado estado);

	/** Bloquea la fila: dos personas no pueden decidir a la vez sobre el mismo documento. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Importacion i where i.id = :id")
	Optional<Importacion> bloquear(Long id);

	@Query(value = "SELECT pg_database_size(current_database())", nativeQuery = true)
	long tamanioBase();

	long countByEstadoIn(Collection<Importacion.Estado> estados);

	@EntityGraph(attributePaths = "subidoPor")
	List<Importacion> findTop200ByOrderByIdDesc();

	@Modifying
	@Query("update Importacion i set i.estado = :cola where i.estado = :procesando")
	int devolverACola(Importacion.Estado procesando, Importacion.Estado cola);

}
