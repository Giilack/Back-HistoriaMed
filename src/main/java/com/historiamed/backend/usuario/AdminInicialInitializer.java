package com.historiamed.backend.usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.config.HistoriaMedProperties;

import lombok.RequiredArgsConstructor;

/**
 * Crea el primer ADMIN al arrancar, solo si todavía no existe ninguno. Sin él nadie podría entrar a crear usuarios.
 * Usa ADMIN_INICIAL_USERNAME y ADMIN_INICIAL_PASSWORD del .env; deberá cambiar la contraseña en su primer ingreso.
 */
@Component
@RequiredArgsConstructor
public class AdminInicialInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminInicialInitializer.class);

	private final UsuarioRepository repository;

	private final PasswordEncoder passwordEncoder;

	private final HistoriaMedProperties properties;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (repository.existsByRol(Rol.ADMIN)) {
			return;
		}
		HistoriaMedProperties.AdminInicial admin = properties.adminInicial();
		if (admin == null || admin.username() == null || admin.username().isBlank() || admin.password() == null
				|| admin.password().isBlank()) {
			log.warn("No existe ningún ADMIN. Define ADMIN_INICIAL_USERNAME y ADMIN_INICIAL_PASSWORD en backend/.env "
					+ "y reinicia la aplicación para crearlo.");
			return;
		}

		Usuario u = new Usuario();
		u.setUsername(admin.username());
		u.setPasswordHash(passwordEncoder.encode(admin.password()));
		u.setNombres("Administrador");
		u.setApellidos("del Sistema");
		// DNI reservado para la cuenta técnica inicial; se puede corregir luego desde la gestión de usuarios
		u.setDni("00000000");
		u.setRol(Rol.ADMIN);
		u.setDebeCambiarPassword(true);
		repository.save(u);
		log.info("Se creó el ADMIN inicial '{}'. Deberá cambiar su contraseña en el primer ingreso.", u.getUsername());
	}

}
