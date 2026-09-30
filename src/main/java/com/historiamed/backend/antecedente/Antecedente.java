package com.historiamed.backend.antecedente;

import java.time.LocalDate;

import com.historiamed.backend.catalogo.Cie10;
import com.historiamed.backend.catalogo.Medicamento;
import com.historiamed.backend.common.entity.EntidadBase;
import com.historiamed.backend.documento.Documento;
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
 * Antecedente del paciente. No se borra (plan.md, principio P3): si fue un error, se inactiva con un motivo.
 */
@Getter
@Setter
@Entity
@Table(name = "antecedentes")
public class Antecedente extends EntidadBase {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "paciente_id", nullable = false, updatable = false)
	private Paciente paciente;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoAntecedente tipo;

	@Column(nullable = false)
	private String descripcion;

	private String detalle;

	/** Fecha del hecho (cirugía, diagnóstico...), si se conoce. */
	private LocalDate fecha;

	/** Solo en {@link TipoAntecedente#DIAGNOSTICO_PREVIO}. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cie_codigo", updatable = false)
	private Cie10 cie10;

	/** Solo en {@link TipoAntecedente#MEDICACION_HABITUAL}. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "medicamento_id", updatable = false)
	private Medicamento medicamento;

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
