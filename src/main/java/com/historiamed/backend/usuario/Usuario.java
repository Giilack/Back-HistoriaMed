package com.historiamed.backend.usuario;

import java.time.Instant;

import com.historiamed.backend.common.entity.EntidadBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "usuarios")
public class Usuario extends EntidadBase {

	@Column(nullable = false, unique = true)
	private String username;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private String nombres;

	@Column(nullable = false)
	private String apellidos;

	@Column(nullable = false, unique = true)
	private String dni;

	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Rol rol;

	/** Número de colegiatura del Colegio Médico del Perú. Obligatorio para MEDICO. */
	private String cmp;

	@Column(nullable = false)
	private boolean activo = true;

	@Column(name = "debe_cambiar_password", nullable = false)
	private boolean debeCambiarPassword = true;

	@Column(name = "intentos_fallidos", nullable = false)
	private int intentosFallidos;

	@Column(name = "bloqueado_hasta")
	private Instant bloqueadoHasta;

	@Column(name = "ultimo_acceso")
	private Instant ultimoAcceso;

	public boolean estaBloqueado(Instant ahora) {
		return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
	}

	public String nombreCompleto() {
		return nombres + " " + apellidos;
	}

}
