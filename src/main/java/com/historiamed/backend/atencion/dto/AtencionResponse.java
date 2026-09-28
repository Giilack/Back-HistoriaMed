package com.historiamed.backend.atencion.dto;

import java.time.Instant;
import java.util.List;

import com.historiamed.backend.atencion.Adenda;
import com.historiamed.backend.atencion.Atencion;
import com.historiamed.backend.atencion.Diagnostico;
import com.historiamed.backend.atencion.EstadoAtencion;
import com.historiamed.backend.atencion.ItemReceta;
import com.historiamed.backend.atencion.TipoDiagnostico;
import com.historiamed.backend.atencion.ViaAdministracion;
import com.historiamed.backend.common.util.Edad;
import com.historiamed.backend.paciente.Paciente;
import com.historiamed.backend.paciente.Sexo;
import com.historiamed.backend.usuario.Usuario;

public record AtencionResponse(Long id, Long citaId, PacienteAtencion paciente, MedicoAtencion medico,
		EstadoAtencion estado, Instant inicioEn, Instant cerradaEn, String motivoConsulta, String tiempoEnfermedad,
		String anamnesis, String examenFisico, String planTrabajo, String indicaciones,
		List<DiagnosticoResponse> diagnosticos, List<ItemRecetaResponse> receta, List<AdendaResponse> adendas) {

	public record PacienteAtencion(Long id, String numeroHc, String nombreCompleto, String edad, Sexo sexo) {
	}

	public record MedicoAtencion(Long id, String nombreCompleto, String cmp) {
	}

	public record DiagnosticoResponse(String codigo, String descripcion, TipoDiagnostico tipo, boolean principal) {

		static DiagnosticoResponse de(Diagnostico d) {
			return new DiagnosticoResponse(d.getCie10().getCodigo(), d.getCie10().getDescripcion(), d.getTipo(),
					d.isPrincipal());
		}

	}

	public record ItemRecetaResponse(Long medicamentoId, String medicamento, String dosis, ViaAdministracion via,
			String frecuencia, String duracion, Integer cantidad, String indicaciones, boolean alergiaConfirmada,
			String justificacionAlergia) {

		static ItemRecetaResponse de(ItemReceta i) {
			return new ItemRecetaResponse(i.getMedicamento().getId(), i.getMedicamento().descripcion(), i.getDosis(),
					i.getVia(), i.getFrecuencia(), i.getDuracion(), i.getCantidad(), i.getIndicaciones(),
					i.isAlergiaConfirmada(), i.getJustificacionAlergia());
		}

	}

	public record AdendaResponse(Long id, String autor, String texto, Instant creadoEn) {

		static AdendaResponse de(Adenda a) {
			return new AdendaResponse(a.getId(), nombre(a.getAutor()), a.getTexto(), a.getCreadoEn());
		}

	}

	public static AtencionResponse de(Atencion a) {
		Paciente p = a.getPaciente();
		Usuario m = a.getMedico();
		return new AtencionResponse(a.getId(), a.getCita().getId(),
				new PacienteAtencion(p.getId(), p.getNumeroHc(), p.nombreCompleto(), Edad.texto(p.getFechaNacimiento()),
						p.getSexo()),
				new MedicoAtencion(m.getId(), nombre(m), m.getCmp()), a.getEstado(), a.getInicioEn(), a.getCerradaEn(),
				a.getMotivoConsulta(), a.getTiempoEnfermedad(), a.getAnamnesis(), a.getExamenFisico(),
				a.getPlanTrabajo(), a.getIndicaciones(),
				a.getDiagnosticos().stream().map(DiagnosticoResponse::de).toList(),
				a.getReceta().stream().map(ItemRecetaResponse::de).toList(),
				a.getAdendas().stream().map(AdendaResponse::de).toList());
	}

	private static String nombre(Usuario u) {
		return u.getApellidos() + ", " + u.getNombres();
	}

}
