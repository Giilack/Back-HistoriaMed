package com.historiamed.backend.atencion.dto;

import java.time.LocalDate;
import java.util.List;

import com.historiamed.backend.atencion.ItemPlan;
import com.historiamed.backend.atencion.TipoDiagnostico;
import com.historiamed.backend.atencion.ViaAdministracion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Contenido de la atención. Cada guardado reemplaza el borrador completo (incluidos diagnósticos, receta y plan).
 *
 * @param plan tratamiento no farmacológico, exámenes solicitados e interconsultas (puede omitirse)
 * @param descanso descanso médico, o null si no corresponde
 * @param control cita de control sugerida, o null si no corresponde
 */
public record AtencionRequest(
		@NotBlank @Size(max = 500) String motivoConsulta,
		@Size(max = 60) String tiempoEnfermedad,
		@Size(max = 4000) String anamnesis,
		@Size(max = 4000) String examenFisico,
		@Size(max = 2000) String planTrabajo,
		@Size(max = 2000) String indicaciones,
		@NotNull @Size(max = 10) List<@Valid DiagnosticoRequest> diagnosticos,
		@NotNull @Size(max = 15) List<@Valid ItemRecetaRequest> receta,
		@Size(max = 20) List<@Valid ItemPlanRequest> plan,
		@Valid DescansoRequest descanso,
		@Valid ControlRequest control) {

	public record DiagnosticoRequest(@NotBlank String codigo, @NotNull TipoDiagnostico tipo, boolean principal) {
	}

	/**
	 * @param categoria obligatoria en TRATAMIENTO y EXAMEN; no se usa en INTERCONSULTA
	 * @param descripcion la indicación, el examen o la especialidad
	 * @param detalle una nota; en la interconsulta, el motivo (obligatorio)
	 */
	public record ItemPlanRequest(
			@NotNull ItemPlan.Tipo tipo,
			ItemPlan.Categoria categoria,
			@NotBlank @Size(max = 200) String descripcion,
			@Size(max = 300) String detalle) {
	}

	public record DescansoRequest(@NotNull @Min(1) @Max(30) Integer dias, @NotNull LocalDate desde) {
	}

	public record ControlRequest(@NotNull LocalDate fecha, @Size(max = 200) String nota) {
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
