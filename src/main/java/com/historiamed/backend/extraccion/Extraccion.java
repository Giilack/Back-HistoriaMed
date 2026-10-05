package com.historiamed.backend.extraccion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.historiamed.backend.common.entity.EntidadBase;
import com.historiamed.backend.documento.Documento;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.usuario.Usuario;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Revisión de un documento clínico: los datos que contiene, clasificados por categoría, a la espera de que un médico
 * los valide (plan.md, sección 5.5). Mientras está pendiente es un borrador; al validarla o rechazarla queda cerrada.
 */
@Getter
@Setter
@Entity
@Table(name = "extracciones")
public class Extraccion extends EntidadBase {

	public enum Origen {
		/** Los datos los escribe una persona (TRIAJE o MEDICO). */
		MANUAL,
		/** Los datos los propone el servicio de IA (fase 11). */
		IA
	}

	/** Estado del procesamiento con IA; nulo si no se usó. */
	public enum EstadoIa {
		PROCESANDO,
		COMPLETADA,
		ERROR
	}

	public enum Estado {
		PENDIENTE_REVISION,
		/** El médico decidió sobre cada dato; los aceptados ya están en la historia clínica. */
		VALIDADA,
		/** El médico descartó la revisión completa (documento ilegible, de otro paciente...). */
		RECHAZADA
	}

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "documento_id", nullable = false, updatable = false)
	private Documento documento;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "paciente_id", nullable = false, updatable = false)
	private Paciente paciente;

	/** Pasa a IA cuando la IA propone los datos (fase 11). */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Origen origen = Origen.MANUAL;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Estado estado = Estado.PENDIENTE_REVISION;

	/** Texto completo leído del documento (lo llenará la IA). */
	@Column(name = "texto_completo")
	private String textoCompleto;

	@Column(name = "motivo_rechazo")
	private String motivoRechazo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "creado_por", nullable = false, updatable = false)
	private Usuario creadoPor;

	/** Médico que validó o rechazó. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "revisado_por")
	private Usuario revisadoPor;

	@Column(name = "revisado_en")
	private Instant revisadoEn;

	@Enumerated(EnumType.STRING)
	@Column(name = "ia_estado")
	private EstadoIa iaEstado;

	/** Modelo con el que se generó la propuesta (por ejemplo gemini-3.8-flash). */
	@Column(name = "ia_modelo")
	private String iaModelo;

	/** Motivo del fallo, si la IA no pudo procesar el documento. */
	@Column(name = "ia_error")
	private String iaError;

	@Column(name = "ia_solicitada_en")
	private Instant iaSolicitadaEn;

	@Column(name = "ia_terminada_en")
	private Instant iaTerminadaEn;

	@OneToMany(mappedBy = "extraccion", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<ExtraccionItem> items = new ArrayList<>();

	public boolean estaPendiente() {
		return estado == Estado.PENDIENTE_REVISION;
	}

	public boolean iaProcesando() {
		return iaEstado == EstadoIa.PROCESANDO;
	}

}
