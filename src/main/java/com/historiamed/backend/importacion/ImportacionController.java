package com.historiamed.backend.importacion;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.historiamed.backend.importacion.dto.AprobacionRequest;
import com.historiamed.backend.importacion.dto.AprobacionResponse;
import com.historiamed.backend.importacion.dto.EspacioResponse;
import com.historiamed.backend.importacion.dto.ImportacionDetalle;
import com.historiamed.backend.importacion.dto.ImportacionResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Importación masiva de historias en PDF: solo Admisión. */
@RestController
@RequestMapping("/api/importaciones")
@PreAuthorize("hasRole('ADMISION')")
@RequiredArgsConstructor
public class ImportacionController {

	private final ImportacionService service;

	public record RechazoRequest(@NotBlank @Size(max = 300) String motivo) {
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ImportacionResponse subir(@RequestParam MultipartFile archivo) {
		return service.subir(archivo);
	}

	@GetMapping
	public List<ImportacionResponse> listar() {
		return service.listar();
	}

	@GetMapping("/{id}")
	public ImportacionDetalle ver(@PathVariable Long id) {
		return service.ver(id);
	}

	@GetMapping("/{id}/archivo")
	public ResponseEntity<Resource> archivo(@PathVariable Long id) {
		Resource contenido = service.archivo(id);
		Importacion i = service.obtener(id);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(i.getContentType()))
			.cacheControl(CacheControl.noStore())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline().filename(i.getNombreOriginal(), StandardCharsets.UTF_8).build().toString())
			.body(contenido);
	}

	@PostMapping("/{id}/aprobacion")
	public AprobacionResponse aprobar(@PathVariable Long id, @Valid @RequestBody AprobacionRequest req) {
		return service.aprobar(id, req);
	}

	@PostMapping("/{id}/rechazo")
	public ImportacionResponse rechazar(@PathVariable Long id, @Valid @RequestBody RechazoRequest req) {
		return service.rechazar(id, req.motivo());
	}

	@DeleteMapping("/{id}")
	public ImportacionResponse eliminar(@PathVariable Long id) {
		return service.eliminar(id);
	}

	@PostMapping("/{id}/anulacion")
	public ImportacionResponse anular(@PathVariable Long id, @Valid @RequestBody RechazoRequest req) {
		return service.anular(id, req.motivo());
	}

	@GetMapping("/espacio")
	public EspacioResponse espacio() {
		return service.espacio();
	}

	/** Aviso para quien atiende: cuántos documentos del paciente se importaron sin revisión médica. */
	@GetMapping("/del-paciente/{pacienteId}")
	@PreAuthorize("hasAnyRole('TRIAJE', 'MEDICO')")
	public long delPaciente(@PathVariable Long pacienteId) {
		return service.contarDelPaciente(pacienteId);
	}

	@PostMapping("/{id}/reintento")
	public ImportacionResponse reintentar(@PathVariable Long id) {
		return service.reintentar(id);
	}

}
