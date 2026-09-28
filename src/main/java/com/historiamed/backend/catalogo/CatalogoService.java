package com.historiamed.backend.catalogo;

import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.historiamed.backend.common.exception.RecursoNoEncontradoException;
import com.historiamed.backend.common.exception.ReglaNegocioException;
import com.historiamed.backend.common.util.Texto;

import lombok.RequiredArgsConstructor;

/**
 * Catálogos CIE-10 y de medicamentos. Son pequeños (decenas de filas), así que se filtran en memoria sin
 * distinguir tildes ni mayúsculas. Si se importa el CIE-10 completo, conviene pasar esta búsqueda a la BD.
 */
@Service
@RequiredArgsConstructor
public class CatalogoService {

	private static final int MAXIMO_RESULTADOS = 20;

	private final Cie10Repository cie10Repository;

	private final MedicamentoRepository medicamentoRepository;

	/** Busca por código ("J02", "j02.9") o por palabras de la descripción ("faringitis aguda"). */
	@Transactional(readOnly = true)
	public List<Cie10> buscarCie10(String q) {
		String consulta = Texto.normalizar(q);
		return cie10Repository.findAll(Sort.by("codigo"))
			.stream()
			.filter(c -> consulta.isEmpty() || Texto.normalizar(c.getCodigo()).startsWith(consulta)
					|| contieneTodas(c.getDescripcion(), consulta))
			.limit(MAXIMO_RESULTADOS)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<Medicamento> buscarMedicamentos(String q) {
		String consulta = Texto.normalizar(q);
		return medicamentoRepository.findByActivoTrueOrderByNombreAscConcentracionAsc()
			.stream()
			.filter(m -> consulta.isEmpty() || contieneTodas(m.getNombre() + " " + m.getPrincipioActivo() + " "
					+ m.getConcentracion() + " " + m.getFormaFarmaceutica(), consulta))
			.limit(MAXIMO_RESULTADOS)
			.toList();
	}

	@Transactional(readOnly = true)
	public Cie10 obtenerCie10(String codigo) {
		return cie10Repository.findById(codigo.trim().toUpperCase())
			.orElseThrow(() -> new RecursoNoEncontradoException("Diagnóstico CIE-10", codigo));
	}

	@Transactional(readOnly = true)
	public Medicamento obtenerMedicamento(Long id) {
		Medicamento m = medicamentoRepository.findById(id)
			.orElseThrow(() -> new RecursoNoEncontradoException("Medicamento", id));
		if (!m.isActivo()) {
			throw new ReglaNegocioException("El medicamento " + m.descripcion() + " ya no está disponible");
		}
		return m;
	}

	private static boolean contieneTodas(String texto, String consulta) {
		String t = Texto.normalizar(texto);
		return Arrays.stream(consulta.split(" ")).allMatch(t::contains);
	}

}
