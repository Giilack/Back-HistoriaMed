package com.historiamed.backend.common.entity;

import com.historiamed.backend.documento.Documento;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Campos comunes de los datos clínicos que se registran a mano o desde un documento validado (antecedentes y
 * resultados de laboratorio): paciente, documento de origen, quién lo registró y el estado. No se borran (plan.md,
 * principio P3): si fueron un error, se inactivan con un motivo.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class DatoClinicoInactivable extends EntidadBase {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "paciente_id", nullable = false, updatable = false)
	private Paciente paciente;

	/** Documento del que salió el dato; nulo si se registró directamente. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "documento_id", updatable = false)
	private Documento documento;

	@Column(nullable = false)
	private boolean activo = true;

	@Column(name = "motivo_inactivacion")
	private String motivoInactivacion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "registrado_por", nullable = false, updatable = false)
	private Usuario registradoPor;

}
