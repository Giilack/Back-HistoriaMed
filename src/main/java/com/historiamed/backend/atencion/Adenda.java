package com.historiamed.backend.atencion;

import java.time.Instant;

import com.historiamed.backend.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Corrección o aclaración a una atención ya cerrada. No modifica la atención: queda a continuación, con su autor
 * y fecha (plan.md, principio P3).
 */
@Getter
@Entity
@Table(name = "atencion_adendas")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Adenda {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "atencion_id", nullable = false, updatable = false)
	private Atencion atencion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "autor_id", nullable = false, updatable = false)
	private Usuario autor;

	@Column(nullable = false, updatable = false)
	private String texto;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	Adenda(Atencion atencion, Usuario autor, String texto) {
		this.atencion = atencion;
		this.autor = autor;
		this.texto = texto;
		this.creadoEn = Instant.now();
	}

}
