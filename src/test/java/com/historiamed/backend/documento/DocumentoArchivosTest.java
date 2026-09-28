package com.historiamed.backend.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.historiamed.backend.TestSeguridad;
import com.historiamed.backend.config.HistoriaMedProperties;

class DocumentoArchivosTest {

	@TempDir
	Path carpeta;

	@Test
	void detectaElFormatoPorElContenidoNoPorLaExtension() {
		assertThat(FormatoArchivo.detectar("%PDF-1.7 ...".getBytes(StandardCharsets.US_ASCII))).contains(FormatoArchivo.PDF);
		assertThat(FormatoArchivo.detectar(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0 }))
			.contains(FormatoArchivo.PNG);
		assertThat(FormatoArchivo.detectar(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 }))
			.contains(FormatoArchivo.JPEG);
		// Un ejecutable de Windows ("MZ") renombrado a .pdf se rechaza
		assertThat(FormatoArchivo.detectar(new byte[] { 'M', 'Z', (byte) 0x90, 0 })).isEmpty();
		assertThat(FormatoArchivo.detectar("<html>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
		assertThat(FormatoArchivo.detectar(new byte[0])).isEmpty();
	}

	@Test
	void guardaYRecuperaConUnaClaveGeneradaPorElSistema() throws Exception {
		AlmacenamientoLocal almacenamiento = new AlmacenamientoLocal(propiedades());
		byte[] contenido = "%PDF-1.4 prueba".getBytes(StandardCharsets.US_ASCII);

		String clave = almacenamiento.guardar(contenido, "pdf");

		assertThat(clave).matches("\\d{4}/\\d{2}/[0-9a-f-]{36}\\.pdf");
		assertThat(almacenamiento.obtener(clave).getContentAsByteArray()).isEqualTo(contenido);
		assertThat(Files.exists(carpeta.resolve(clave))).isTrue();
	}

	@Test
	void noPermiteSalirDeLaCarpetaDelAlmacenamiento() {
		AlmacenamientoLocal almacenamiento = new AlmacenamientoLocal(propiedades());

		assertThatThrownBy(() -> almacenamiento.obtener("../../.env")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void elNombreDelArchivoSeLimpia() {
		assertThat(DocumentoService.nombreSeguro("C:\\Users\\x\\Escritorio\\hemograma.pdf", FormatoArchivo.PDF))
			.isEqualTo("hemograma.pdf");
		assertThat(DocumentoService.nombreSeguro("../../etc/passwd", FormatoArchivo.PDF)).isEqualTo("passwd");
		assertThat(DocumentoService.nombreSeguro("mal\"nombre\n.png", FormatoArchivo.PNG)).isEqualTo("malnombre.png");
		assertThat(DocumentoService.nombreSeguro(null, FormatoArchivo.JPEG)).isEqualTo("documento.jpg");
	}

	@Test
	void huellaSha256() {
		assertThat(DocumentoService.sha256("abc".getBytes(StandardCharsets.US_ASCII)))
			.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	private HistoriaMedProperties propiedades() {
		HistoriaMedProperties base = TestSeguridad.propiedades();
		return new HistoriaMedProperties(base.jwt(), base.seguridad(), base.cors(), base.adminInicial(),
				new HistoriaMedProperties.Almacenamiento(carpeta.toString()));
	}

}
