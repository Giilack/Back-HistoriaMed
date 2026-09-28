package com.historiamed.backend.cita.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.historiamed.backend.cita.Cita;
import com.historiamed.backend.cita.EstadoCita;
import com.historiamed.backend.cita.Prioridad;
import com.historiamed.backend.common.util.Edad;
import com.historiamed.backend.consultorio.dto.ConsultorioResponse;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.Sexo;
import com.historiamed.backend.paciente.TipoFinanciamiento;
import com.historiamed.backend.usuario.dto.MedicoResponse;

/**
 * Cita con los datos mínimos para agendas y colas (sin datos clínicos).
 */
public record CitaResponse(Long id, LocalDate fecha, LocalTime hora, boolean sinCita, EstadoCita estado,
		Integer numeroTurno, Prioridad prioridad, String motivo, PacienteCita paciente, MedicoResponse medico,
		ConsultorioResponse consultorio, Instant llegadaEn, Instant triajeEn, Instant consultaInicioEn,
		Instant atendidoEn, Instant canceladaEn, String motivoCancelacion, Instant creadoEn) {

	public record PacienteCita(Long id, String numeroHc, String nombreCompleto, String edad, Sexo sexo,
			TipoFinanciamiento tipoFinanciamiento) {

		static PacienteCita de(Paciente p) {
			return new PacienteCita(p.getId(), p.getNumeroHc(), p.nombreCompleto(),
					Edad.texto(p.getFechaNacimiento()), p.getSexo(), p.getTipoFinanciamiento());
		}

	}

	public static CitaResponse de(Cita c) {
		return new CitaResponse(c.getId(), c.getFecha(), c.getHora(), c.isSinCita(), c.getEstado(),
				c.getNumeroTurno(), c.getPrioridad(), c.getMotivo(), PacienteCita.de(c.getPaciente()),
				MedicoResponse.de(c.getMedico()), ConsultorioResponse.de(c.getConsultorio()), c.getLlegadaEn(),
				c.getTriajeEn(),
				c.getConsultaInicioEn(), c.getAtendidoEn(), c.getCanceladaEn(), c.getMotivoCancelacion(),
				c.getCreadoEn());
	}

}
