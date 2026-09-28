package com.historiamed.backend.consultorio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConsultorioRequest(@NotBlank @Size(max = 60) String nombre, @NotBlank @Size(max = 60) String especialidad) {
}
