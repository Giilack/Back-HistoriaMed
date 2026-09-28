package com.historiamed.backend.paciente.dto;

import com.historiamed.backend.paciente.EstadoSeguro;

import jakarta.validation.constraints.NotNull;

/**
 * Resultado de la verificación manual del seguro (ADMISION consultó el padrón y registra lo que encontró).
 */
public record VerificacionSeguroRequest(@NotNull EstadoSeguro estado) {
}
