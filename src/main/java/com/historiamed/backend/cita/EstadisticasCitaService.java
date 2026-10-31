package com.historiamed.backend.cita;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.cita.dto.PromediosCita;
import com.historiamed.backend.common.dto.Conteo;
import com.historiamed.backend.common.dto.ConteoPorFecha;

import lombok.RequiredArgsConstructor;

/** Indicadores agregados de las citas de un período, para el módulo de reportes. Ninguno identifica a un paciente. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EstadisticasCitaService {

	private final CitaRepository repository;

	public List<Conteo> porEstado(LocalDate desde, LocalDate hasta) {
		return repository.contarPorEstado(desde, hasta);
	}

	public PromediosCita promedios(LocalDate desde, LocalDate hasta) {
		return repository.promediarTiempos(desde, hasta);
	}

	public List<ConteoPorFecha> atendidasPorDia(LocalDate desde, LocalDate hasta) {
		return repository.contarAtendidasPorDia(desde, hasta);
	}

	public List<Conteo> atendidasPorMedico(LocalDate desde, LocalDate hasta) {
		return repository.contarAtendidasPorMedico(desde, hasta);
	}

	public List<Conteo> atendidasPorConsultorio(LocalDate desde, LocalDate hasta) {
		return repository.contarAtendidasPorConsultorio(desde, hasta);
	}

	public List<Conteo> porPrioridad(LocalDate desde, LocalDate hasta) {
		return repository.contarPorPrioridad(desde, hasta);
	}

	public List<Conteo> pacientesPorFinanciamiento(LocalDate desde, LocalDate hasta) {
		return repository.contarPacientesPorFinanciamiento(desde, hasta);
	}

}
