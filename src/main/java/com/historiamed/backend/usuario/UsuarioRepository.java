package com.historiamed.backend.usuario;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

	Optional<Usuario> findByUsername(String username);

	boolean existsByUsername(String username);

	boolean existsByDni(String dni);

	boolean existsByDniAndIdNot(String dni, Long id);

	boolean existsByEmail(String email);

	boolean existsByEmailAndIdNot(String email, Long id);

	boolean existsByRol(Rol rol);

	long countByRolAndActivoTrue(Rol rol);

}
