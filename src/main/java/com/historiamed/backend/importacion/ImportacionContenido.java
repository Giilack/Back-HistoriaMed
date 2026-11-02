package com.historiamed.backend.importacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** El archivo mientras no se aprueba. Va aparte para que listar importaciones no cargue los PDF en memoria. */
@Getter
@Entity
@Table(name = "importacion_contenidos")
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportacionContenido {

	@Id
	@Column(name = "importacion_id")
	private Long importacionId;

	@Column(nullable = false)
	private byte[] contenido;

}
