package com.historiamed.backend.atencion;

import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Una indicación del plan de la atención que no es un medicamento (plan.md, sección 5.4): tratamiento no
 * farmacológico, examen auxiliar solicitado o interconsulta.
 */
@Getter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemPlan {

	public enum Tipo {
		/** Dieta, reposo, fisioterapia, curaciones... */
		TRATAMIENTO(Set.of(Categoria.DIETA, Categoria.REPOSO, Categoria.FISIOTERAPIA, Categoria.CURACION,
				Categoria.OTRO)),
		/** Examen auxiliar que se solicita. */
		EXAMEN(Set.of(Categoria.LABORATORIO, Categoria.IMAGEN, Categoria.OTRO)),
		/** Derivación a otra especialidad: la descripción es la especialidad y el detalle, el motivo. */
		INTERCONSULTA(Set.of());

		private final Set<Categoria> categorias;

		Tipo(Set<Categoria> categorias) {
			this.categorias = categorias;
		}

		/** Categorías que admite este tipo (vacío: no lleva categoría). */
		public Set<Categoria> categorias() {
			return categorias;
		}
	}

	public enum Categoria {
		DIETA,
		REPOSO,
		FISIOTERAPIA,
		CURACION,
		LABORATORIO,
		IMAGEN,
		OTRO
	}

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Tipo tipo;

	@Enumerated(EnumType.STRING)
	private Categoria categoria;

	/** La indicación, el examen solicitado o la especialidad a la que se deriva. */
	@Column(nullable = false)
	private String descripcion;

	/** Una nota o, en la interconsulta, el motivo. */
	private String detalle;

}
