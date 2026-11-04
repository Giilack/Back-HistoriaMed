package com.historiamed.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.historiamed.backend.common.security.EscritorErrorJson;

import tools.jackson.databind.json.JsonMapper;

class LimiteLoginFilterTest {

	private final EscritorErrorJson escritor = new EscritorErrorJson(JsonMapper.builder().findAndAddModules().build());

	private static MockHttpServletRequest login() {
		return new MockHttpServletRequest("POST", LimiteLoginFilter.RUTA);
	}

	@Test
	void sinPermisoLibreRespondeServicioOcupado() throws Exception {
		LimiteLoginFilter filtro = new LimiteLoginFilter(escritor, 1, 50);
		CountDownLatch dentro = new CountDownLatch(1);
		CountDownLatch soltar = new CountDownLatch(1);
		ExecutorService hilo = Executors.newSingleThreadExecutor();
		Future<?> primero = hilo.submit(() -> {
			filtro.doFilter(login(), new MockHttpServletResponse(), (req, res) -> {
				dentro.countDown();
				try {
					soltar.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
			return null;
		});
		assertThat(dentro.await(5, TimeUnit.SECONDS)).isTrue();

		MockHttpServletResponse segunda = new MockHttpServletResponse();
		filtro.doFilter(login(), segunda, (req, res) -> {
			throw new AssertionError("no debía pasar");
		});
		assertThat(segunda.getStatus()).isEqualTo(503);
		assertThat(segunda.getHeader("Retry-After")).isEqualTo("5");
		assertThat(segunda.getContentAsString()).contains("SERVICIO_OCUPADO");

		soltar.countDown();
		primero.get(5, TimeUnit.SECONDS);
		hilo.shutdown();

		MockHttpServletResponse tercera = new MockHttpServletResponse();
		boolean[] paso = { false };
		filtro.doFilter(login(), tercera, (req, res) -> paso[0] = true);
		assertThat(paso[0]).as("al liberar el permiso, el siguiente entra").isTrue();
	}

	@Test
	void noAfectaAOtrasRutas() throws Exception {
		LimiteLoginFilter filtro = new LimiteLoginFilter(escritor, 0, 10);
		boolean[] paso = { false };
		filtro.doFilter(new MockHttpServletRequest("GET", "/api/pacientes"), new MockHttpServletResponse(),
				(req, res) -> paso[0] = true);
		assertThat(paso[0]).isTrue();
	}
}
