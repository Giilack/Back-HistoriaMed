package com.historiamed.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

// Requiere Docker (Testcontainers levanta PostgreSQL). Sin Docker, la prueba se omite en lugar de fallar.
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HistoriaMedApplicationTests {

	@Test
	void contextLoads() {
	}

}
