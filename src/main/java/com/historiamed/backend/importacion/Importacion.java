package com.historiamed.backend.importacion;

import java.time.Instant;

import com.historiamed.backend.common.entity.EntidadBase;
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

/** Un PDF de una historia antigua que aún no pertenece a ningún paciente (plan.md, sección 5.5.1). */
@Getter
@Setter
@Entity
@Table(name = "importaciones")
public class Importacion extends EntidadBase {

	public enum Estado {
		EN_COLA,
		PROCESANDO,
		/** La IA terminó: espera la decisión de Admisión. */
		LISTA,
		ERROR,
		APROBADA,
		RECHAZADA
	}

	@Column(name = "nombre_original", nullable = false, updatable = false)
	private String nombreOriginal;

	@Column(name = "content_type", nullable = false, updatable = false)
	private String contentType;

	@Column(name = "tamanio_bytes", nullable = false, updatable = false)
	private Long tamanioBytes;

	@Column(nullable = false, updatable = false)
	private String sha256;

	@Column(name = "clave_almacenamiento", nullable = false, updatable = false)
	private String claveAlmacenamiento;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Estado estado = Estado.EN_COLA;

	@Column(nullable = false)
	private int intentos;

	private String error;

	private String modelo;

	/** JSON con la propuesta de la IA, cifrado. */
	@Column(name = "datos_cifrados")
	private String datosCifrados;

	@Column(name = "paciente_id")
	private Long pacienteId;

	@Column(name = "documento_id")
	private Long documentoId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subido_por", nullable = false, updatable = false)
	private Usuario subidoPor;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "revisado_por")
	private Usuario revisadoPor;

	@Column(name = "revisado_en")
	private Instant revisadoEn;

	@Column(name = "motivo_rechazo")
	private String motivoRechazo;

	public boolean abierta() {
		return estado != Estado.APROBADA && estado != Estado.RECHAZADA;
	}

}
