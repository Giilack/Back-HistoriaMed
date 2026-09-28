package com.historiamed.backend.documento;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMISION', 'TRIAJE', 'MEDICO')")
@RequiredArgsConstructor
public class DocumentoController {

	private final DocumentoService service;

	public record AnulacionRequest(@NotBlank @Size(max = 200) String motivo) {
	}

	/** Subida (multipart/form-data). Máximo 10 MB; PDF, JPG o PNG. */
	@PostMapping(path = "/pacientes/{pacienteId}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentoResponse subir(@PathVariable Long pacienteId, @RequestParam MultipartFile archivo,
			@RequestParam TipoDocumentoClinico tipo, @RequestParam(required = false) String descripcion,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDocumento,
			@RequestParam(required = false) Long citaId) {
		return service.subir(pacienteId, archivo, tipo, descripcion, fechaDocumento, citaId);
	}

	@GetMapping("/pacientes/{pacienteId}/documentos")
	public List<DocumentoResponse> listar(@PathVariable Long pacienteId) {
		return service.listar(pacienteId);
	}

	/** Contenido del documento (dato clínico: solo TRIAJE y MEDICO). Se muestra en el navegador, no se cachea. */
	@GetMapping("/documentos/{id}/archivo")
	@PreAuthorize("hasAnyRole('TRIAJE', 'MEDICO')")
	public ResponseEntity<Resource> descargar(@PathVariable Long id) {
		DocumentoService.Archivo archivo = service.descargar(id);
		Documento d = archivo.documento();
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(d.getContentType()))
			.contentLength(d.getTamanioBytes())
			.cacheControl(CacheControl.noStore())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline().filename(d.getNombreOriginal(), StandardCharsets.UTF_8).build().toString())
			.body(archivo.contenido());
	}

	@PatchMapping("/documentos/{id}/anular")
	public DocumentoResponse anular(@PathVariable Long id, @Valid @RequestBody AnulacionRequest req) {
		return service.anular(id, req.motivo());
	}

}
