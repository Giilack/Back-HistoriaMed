package com.historiamed.backend.laboratorio;

import java.time.LocalDate;

import com.historiamed.backend.common.entity.EntidadBase;
import com.historiamed.backend.documento.Documento;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Resultado de un examen de laboratorio. No se borra (plan.md, principio P3): si fue un error, se inactiva con un
 * motivo.
 */
@Getter
@Setter
@Entity
@Table(name = "resultados_laboratorio")
public class ResultadoLaboratorio extends EntidadBase {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "paciente_id", nullable = false, updatable = false)
	private Paciente paciente;

	@Column(nullable = false)
	private String examen;

	/** Texto, no número: hay resultados como "Negativo" o "1/160". */
	@Column(nullable = false)
	private String valor;

	private String unidad;

	@Column(name = "rango_referencia")
	private String rangoReferencia;

	/** Fecha del examen (la del documento), no la de registro. */
	private LocalDate fecha;

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
