package com.historiamed.backend.documento;

import java.time.LocalDate;

import com.historiamed.backend.cita.Cita;
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
 * Documento clínico del paciente. No se borra (plan.md, principio P3): si se subió por error, se anula con un
 * motivo y su contenido deja de estar disponible.
 */
@Getter
@Setter
@Entity
@Table(name = "documentos")
public class Documento extends EntidadBase {

	public enum Estado {
		RECIBIDO,
		ANULADO
	}

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "paciente_id", nullable = false, updatable = false)
	private Paciente paciente;

	/** Cita en la que se entregó el documento (opcional). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cita_id", updatable = false)
	private Cita cita;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoDocumentoClinico tipo;

	private String descripcion;

	/** Fecha que figura en el documento (por ejemplo, la del análisis), no la de subida. */
	@Column(name = "fecha_documento")
	private LocalDate fechaDocumento;

	@Column(name = "nombre_original", nullable = false, updatable = false)
	private String nombreOriginal;

	@Column(name = "content_type", nullable = false, updatable = false)
	private String contentType;

	@Column(name = "tamanio_bytes", nullable = false, updatable = false)
	private Long tamanioBytes;

	@Column(name = "clave_almacenamiento", nullable = false, unique = true, updatable = false)
	private String claveAlmacenamiento;

	@Column(nullable = false, updatable = false)
	private String sha256;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Estado estado = Estado.RECIBIDO;

	@Column(name = "motivo_anulacion")
	private String motivoAnulacion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subido_por", nullable = false, updatable = false)
	private Usuario subidoPor;

}
