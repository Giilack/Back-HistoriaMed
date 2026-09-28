package com.historiamed.backend.atencion.dto;

import java.util.List;

import com.historiamed.backend.atencion.TipoDiagnostico;
import com.historiamed.backend.atencion.ViaAdministracion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Contenido de la atención. Cada guardado reemplaza el borrador completo (incluidos diagnósticos y receta).
 */
public record AtencionRequest(
		@NotBlank @Size(max = 500) String motivoConsulta,
		@Size(max = 60) String tiempoEnfermedad,
		@Size(max = 4000) String anamnesis,
		@Size(max = 4000) String examenFisico,
		@Size(max = 2000) String planTrabajo,
		@Size(max = 2000) String indicaciones,
		@NotNull @Size(max = 10) List<@Valid DiagnosticoRequest> diagnosticos,
		@NotNull @Size(max = 15) List<@Valid ItemRecetaRequest> receta) {

	public record DiagnosticoRequest(@NotBlank String codigo, @NotNull TipoDiagnostico tipo, boolean principal) {
	}

	/**
	 * @param confirmarAlergia true si el médico ya vio la alerta de alergia de este medicamento y decide recetarlo
	 * @param justificacionAlergia obligatoria cuando se confirma
	 */
	public record ItemRecetaRequest(
			@NotNull Long medicamentoId,
			@NotBlank @Size(max = 60) String dosis,
			@NotNull ViaAdministracion via,
			@NotBlank @Size(max = 60) String frecuencia,
			@NotBlank @Size(max = 60) String duracion,
			@NotNull @Min(1) @Max(999) Integer cantidad,
			@Size(max = 200) String indicaciones,
			Boolean confirmarAlergia,
			@Size(max = 300) String justificacionAlergia) {
	}

}
