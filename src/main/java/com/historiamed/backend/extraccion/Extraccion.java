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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
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

	@OneToMany(mappedBy = "extraccion", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<ExtraccionItem> items = new ArrayList<>();

	public boolean estaPendiente() {
		return estado == Estado.PENDIENTE_REVISION;
	}

}
