package com.historiamed.backend.triaje.dto;

import java.math.BigDecimal;

import com.historiamed.backend.cita.Prioridad;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos del triaje. Los rangos rechazan valores imposibles (errores de digitación), no valores anormales: esos
 * se aceptan y generan alertas.
 *
 * @param prioridad si se omite, se usa la sugerida por el sistema. Si es menor que la sugerida, hay que justificar.
 */
public record TriajeRequest(
		@NotBlank @Size(max = 500) String motivoConsulta,
		@Min(40) @Max(300) Integer presionSistolica,
		@Min(20) @Max(200) Integer presionDiastolica,
		@NotNull @Min(20) @Max(250) Integer frecuenciaCardiaca,
		@Min(4) @Max(80) Integer frecuenciaRespiratoria,
		@NotNull @DecimalMin("30.0") @DecimalMax("45.0") @Digits(integer = 2, fraction = 1) BigDecimal temperatura,
		@NotNull @Min(50) @Max(100) Integer saturacion,
		@NotNull @DecimalMin("0.3") @DecimalMax("400") @Digits(integer = 3, fraction = 2) BigDecimal peso,
		@DecimalMin("20") @DecimalMax("250") @Digits(integer = 3, fraction = 1) BigDecimal talla,
		@DecimalMin("20") @DecimalMax("250") @Digits(integer = 3, fraction = 1) BigDecimal perimetroAbdominal,
		Boolean gestante,
		Boolean discapacidad,
		Prioridad prioridad,
		@Size(max = 300) String justificacionPrioridad,
		@Size(max = 500) String observaciones) {
}
