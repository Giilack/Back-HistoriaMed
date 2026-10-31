package com.historiamed.backend.common.dto;

import java.time.LocalDate;

public record ConteoPorFecha(LocalDate fecha, long total) {
}
