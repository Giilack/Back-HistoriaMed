package com.historiamed.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderLimitadoTest {

	/** Codificador falso que cuenta cuántos cálculos corren a la vez. */
	private static final class Contador implements PasswordEncoder {
		final AtomicInteger enCurso = new AtomicInteger();
		final AtomicInteger maximo = new AtomicInteger();

		@Override
		public String encode(CharSequence raw) {
			int ahora = enCurso.incrementAndGet();
			maximo.accumulateAndGet(ahora, Math::max);
			try {
				Thread.sleep(30);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			enCurso.decrementAndGet();
			return "h" + raw;
		}

		@Override
		public boolean matches(CharSequence raw, String encoded) {
			return encode(raw).equals(encoded);
		}
	}

	@Test
	void nuncaCalculaMasContrasenasALaVezQueElTope() throws Exception {
		Contador contador = new Contador();
		PasswordEncoder limitado = new PasswordEncoderLimitado(contador, 3);
		ExecutorService hilos = Executors.newFixedThreadPool(15);
		List<Future<Boolean>> resultados = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			resultados.add(hilos.submit(() -> limitado.matches("clave", "hclave")));
		}
		for (Future<Boolean> r : resultados) {
			assertThat(r.get()).isTrue();
		}
		hilos.shutdown();
		assertThat(contador.maximo.get()).isLessThanOrEqualTo(3);
	}

	@Test
	void conservaElResultadoDelCodificadorReal() {
		PasswordEncoder limitado = new PasswordEncoderLimitado(SecurityConfig.crearPasswordEncoder(), 3);
		String hash = limitado.encode("Clave2026x");
		assertThat(limitado.matches("Clave2026x", hash)).isTrue();
		assertThat(limitado.matches("otra", hash)).isFalse();
		assertThat(limitado.upgradeEncoding(hash)).isFalse();
	}
}
