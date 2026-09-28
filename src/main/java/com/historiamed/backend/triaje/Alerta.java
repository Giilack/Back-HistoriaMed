package com.historiamed.backend.triaje;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Alerta calculada a partir de los signos vitales. Se guarda con el triaje tal como se mostró.
 */
@Getter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alerta {

	@Column(nullable = false)
	private String codigo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Severidad severidad;

	@Column(nullable = false)
	private String mensaje;

}
