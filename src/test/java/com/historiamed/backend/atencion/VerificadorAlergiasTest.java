package com.historiamed.backend.atencion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.historiamed.backend.atencion.VerificadorAlergias.AlergiaActiva;
import com.historiamed.backend.atencion.VerificadorAlergias.Coincidencia;
import com.historiamed.backend.catalogo.Medicamento;

class VerificadorAlergiasTest {

	private final VerificadorAlergias verificador = new VerificadorAlergias();

	private static Medicamento medicamento(String nombre, String principioActivo, String grupo) {
		Medicamento m = new Medicamento();
		ReflectionTestUtils.setField(m, "nombre", nombre);
		ReflectionTestUtils.setField(m, "principioActivo", principioActivo);
		ReflectionTestUtils.setField(m, "concentracion", "500 mg");
		ReflectionTestUtils.setField(m, "formaFarmaceutica", "Tableta");
		ReflectionTestUtils.setField(m, "grupo", grupo);
		return m;
	}

	private static final Medicamento AMOXICILINA = medicamento("Amoxicilina", "amoxicilina", "PENICILINAS");

	private static final Medicamento AMOXI_CLAV = medicamento("Amoxicilina + ácido clavulánico",
			"amoxicilina + acido clavulanico", "PENICILINAS");

	private static final Medicamento CEFALEXINA = medicamento("Cefalexina", "cefalexina", "CEFALOSPORINAS");

	private static final Medicamento COTRIMOXAZOL = medicamento("Sulfametoxazol + trimetoprima",
			"sulfametoxazol + trimetoprima", "SULFONAMIDAS");

	private static final Medicamento SULFATO_FERROSO = medicamento("Sulfato ferroso", "sulfato ferroso", null);

	private static final Medicamento IBUPROFENO = medicamento("Ibuprofeno", "ibuprofeno", "AINES");

	private static final Medicamento AAS = medicamento("Ácido acetilsalicílico", "acido acetilsalicilico", "AINES");

	private static final Medicamento PARACETAMOL = medicamento("Paracetamol", "paracetamol", null);

	private List<Coincidencia> verificar(Medicamento m, String... sustancias) {
		return verificador.verificar(m,
				java.util.Arrays.stream(sustancias).map(s -> new AlergiaActiva(s, "SEVERA")).toList());
	}

	@Test
	void mismoPrincipioActivoSinImportarMayusculasNiTildes() {
		assertThat(verificar(AMOXICILINA, "AMOXICILINA")).hasSize(1);
		assertThat(verificar(AMOXI_CLAV, "Amoxicilina")).singleElement()
			.extracting(Coincidencia::motivo)
			.isEqualTo("contiene amoxicilina");
		assertThat(verificar(AMOXI_CLAV, "ácido clavulánico")).hasSize(1);
		assertThat(verificar(AMOXICILINA, "alergia a la amoxicilina")).hasSize(1);
	}

	@Test
	void alergiaAPenicilinaAlcanzaATodasLasPenicilinas() {
		assertThat(verificar(AMOXICILINA, "Penicilina")).singleElement()
			.extracting(Coincidencia::motivo)
			.isEqualTo("pertenece al grupo PENICILINAS");
		assertThat(verificar(AMOXI_CLAV, "penicilinas")).hasSize(1);
	}

	@Test
	void penicilinaYCefalosporinaPosibleReaccionCruzada() {
		assertThat(verificar(CEFALEXINA, "Penicilina")).singleElement()
			.extracting(Coincidencia::motivo)
			.asString()
			.contains("reacción cruzada");
	}

	@Test
	void alergiaASulfasNoConfundeElSulfatoFerroso() {
		assertThat(verificar(COTRIMOXAZOL, "Sulfas")).hasSize(1);
		assertThat(verificar(SULFATO_FERROSO, "Sulfas")).isEmpty();
		assertThat(verificar(SULFATO_FERROSO, "sulfa")).isEmpty();
	}

	@Test
	void sinonimosYGrupos() {
		assertThat(verificar(AAS, "Aspirina")).hasSize(1);
		assertThat(verificar(IBUPROFENO, "AINES")).hasSize(1);
		assertThat(verificar(COTRIMOXAZOL, "Bactrim")).hasSize(1);
	}

	@Test
	void sinRelacionNoAlerta() {
		assertThat(verificar(PARACETAMOL, "Penicilina", "Mariscos", "Látex")).isEmpty();
		assertThat(verificar(IBUPROFENO, "Penicilina")).isEmpty();
		assertThat(verificar(AMOXICILINA)).isEmpty();
	}

	@Test
	void reportaCadaAlergiaQueCoincide() {
		assertThat(verificar(AMOXICILINA, "Amoxicilina", "Penicilina", "Mariscos")).hasSize(2);
	}

}
