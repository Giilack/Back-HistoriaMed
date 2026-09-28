package com.historiamed.backend.auditoria;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Registro inmutable de auditoría (la base de datos rechaza UPDATE y DELETE; ver V3__crear_auditoria.sql).
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "registros_auditoria")
public class RegistroAuditoria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private Instant fecha;

	@Column(name = "usuario_id", updatable = false)
	private Long usuarioId;

	@Column(updatable = false)
	private String username;

	@Column(updatable = false)
	private String rol;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private AccionAuditoria accion;

	@Column(nullable = false, updatable = false)
	private String recurso;

	@Column(name = "recurso_id", updatable = false)
	private String recursoId;

	@Column(name = "paciente_id", updatable = false)
	private Long pacienteId;

	@Column(updatable = false)
	private String detalle;

	@Column(updatable = false)
	private String ip;

	RegistroAuditoria(Long usuarioId, String username, String rol, AccionAuditoria accion, String recurso,
			String recursoId, Long pacienteId, String detalle, String ip) {
		this.fecha = Instant.now();
		this.usuarioId = usuarioId;
		this.username = username;
		this.rol = rol;
		this.accion = accion;
		this.recurso = recurso;
		this.recursoId = recursoId;
		this.pacienteId = pacienteId;
		this.detalle = detalle;
		this.ip = ip;
	}

}
