package com.historiamed.backend.cita.dto;

/** Promedios en segundos de cada tramo de la cita; null si no hay datos en el período. */
public record PromediosCita(Double llegadaATriaje, Double triajeAConsulta, Double duracionConsulta) {
}
