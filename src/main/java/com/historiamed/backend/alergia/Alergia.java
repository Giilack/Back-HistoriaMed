package com.historiamed.backend.alergia;

import com.historiamed.backend.common.entity.EntidadBase;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Alergia del paciente. No se borra (plan.md, principio P3): si fue un error, se inactiva con un motivo.
 */
@Getter
@Setter
@Entity
@Table(name = "alergias")
public class Alergia extends EntidadBase {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "paciente_id", nullable = false, updatable = false)
	private Paciente paciente;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoAlergia tipo;

	@Column(nullable = false)
	private String sustancia;

	private String reaccion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GravedadAlergia gravedad;

	@Column(nullable = false)
	private boolean activa = true;

	@Column(name = "motivo_inactivacion")
	private String motivoInactivacion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "registrado_por", nullable = false, updatable = false)
	private Usuario registradoPor;

}
