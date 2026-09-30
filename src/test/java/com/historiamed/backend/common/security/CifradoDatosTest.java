package com.historiamed.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class CifradoDatosTest {

	/** Clave de prueba (32 bytes); nunca la de un entorno real. */
	static final String CLAVE_PRUEBA = Base64.getEncoder().encodeToString(new byte[32]);

	private final CifradoDatos cifrado = new CifradoDatos(CLAVE_PRUEBA);

	@Test
	void cifraYDescifraSinExponerElTexto() {
		String valor = cifrado.cifrar("45678912");

		assertThat(valor).startsWith(CifradoDatos.PREFIJO).doesNotContain("45678912");
		assertThat(cifrado.descifrar(valor)).isEqualTo("45678912");
	}

	@Test
	void elMismoTextoProduceCifradosDistintos() {
		assertThat(cifrado.cifrar("Av. Los Olivos 123")).isNotEqualTo(cifrado.cifrar("Av. Los Olivos 123"));
	}

	@Test
	void conservaTildesYEnies() {
		String texto = "Jr. Ñaña 45, Pueblo Libre — interior B";

		assertThat(cifrado.descifrar(cifrado.cifrar(texto))).isEqualTo(texto);
	}

	@Test
	void nuloSigueSiendoNulo() {
		assertThat(cifrado.cifrar(null)).isNull();
		assertThat(cifrado.descifrar(null)).isNull();
		assertThat(cifrado.huella(null)).isNull();
	}

	@Test
	void laHuellaEsEstableYNoRevelaElValor() {
		String huella = cifrado.huella("45678912");

		assertThat(huella).hasSize(64).isEqualTo(cifrado.huella("45678912")).doesNotContain("45678912");
		assertThat(huella).isNotEqualTo(cifrado.huella("45678913"));
	}

	@Test
	void unDatoAlteradoNoSeDescifra() {
		String valor = cifrado.cifrar("987654321");
		// Cambia un carácter dentro del texto cifrado (después del IV)
		int i = CifradoDatos.PREFIJO.length() + 20;
		String alterado = valor.substring(0, i) + (valor.charAt(i) == 'A' ? 'B' : 'A') + valor.substring(i + 1);

		assertThatThrownBy(() -> cifrado.descifrar(alterado)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void otraClaveNoPuedeDescifrar() {
		byte[] otra = new byte[32];
		otra[0] = 1;
		CifradoDatos ajeno = new CifradoDatos(Base64.getEncoder().encodeToString(otra));

		assertThatThrownBy(() -> ajeno.descifrar(cifrado.cifrar("45678912"))).isInstanceOf(IllegalStateException.class);
		assertThat(ajeno.huella("45678912")).isNotEqualTo(cifrado.huella("45678912"));
	}

	@Test
	void exigeUnaClaveDe32Bytes() {
		assertThatThrownBy(() -> new CifradoDatos("")).hasMessageContaining("CIFRADO_CLAVE");
		assertThatThrownBy(() -> new CifradoDatos(Base64.getEncoder().encodeToString(new byte[16])))
			.hasMessageContaining("32 bytes");
	}

}
