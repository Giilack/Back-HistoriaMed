package com.historiamed.backend.atencion;

import com.historiamed.backend.catalogo.Cie10;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diagnostico {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cie10_codigo", nullable = false)
	private Cie10 cie10;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoDiagnostico tipo;

	@Column(nullable = false)
	private boolean principal;

}
