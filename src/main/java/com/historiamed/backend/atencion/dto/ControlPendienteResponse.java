package com.historiamed.backend.atencion.dto;

import java.time.LocalDate;

import com.historiamed.backend.atencion.Atencion;
import com.historiamed.backend.cita.Cita;
import com.historiamed.backend.common.util.Tiempo;

/**
 * Cita de control que un médico sugirió y todavía no se programó. Es lo que ve ADMISION: solo datos para agendar
 * (paciente, médico, consultorio y fecha), sin contenido clínico.
 *
 * @param vencido la fecha sugerida ya pasó
 */
public record ControlPendienteResponse(Long atencionId, Long pacienteId, String numeroHc, String paciente,
		Long medicoId, String medico, Long consultorioId, String consultorio, LocalDate fechaSugerida,
		boolean vencido) {

	public static ControlPendienteResponse de(Atencion a) {
		Cita c = a.getCita();
		return new ControlPendienteResponse(a.getId(), a.getPaciente().getId(), a.getPaciente().getNumeroHc(),
				a.getPaciente().nombreCompleto(), a.getMedico().getId(),
				a.getMedico().getApellidos() + ", " + a.getMedico().getNombres(), c.getConsultorio().getId(),
				c.getConsultorio().getNombre(), a.getControlFecha(), a.getControlFecha().isBefore(Tiempo.hoy()));
	}

}
