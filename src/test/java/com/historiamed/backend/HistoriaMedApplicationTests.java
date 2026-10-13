package com.historiamed.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

// Requiere Docker (Testcontainers levanta PostgreSQL). Sin Docker, la prueba se omite en lugar de fallar.
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HistoriaMedApplicationTests {

	@Autowired
	private ApplicationContext contexto;

	@Test
	void contextLoads() {
		// Arranca con PostgreSQL real: migraciones de Flyway aplicadas y todos los componentes creados
		assertThat(contexto).isNotNull();
		assertThat(contexto.getBeanDefinitionCount()).isPositive();
	}

}
