package com.historiamed.backend.cita.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelacionRequest(@NotBlank @Size(max = 200) String motivo) {
}
