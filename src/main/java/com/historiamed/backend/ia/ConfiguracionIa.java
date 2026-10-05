package com.historiamed.backend.ia;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Hilos para las consultas a la IA. La consulta tarda de segundos a un minuto, así que se hace en segundo plano: la
 * petición del usuario responde al instante y la pantalla consulta el estado. Pocos hilos a propósito: el servidor
 * gratuito tiene 512 MB y la capa gratuita de la IA limita las consultas por minuto.
 */
@Configuration
public class ConfiguracionIa {

	public static final String EJECUTOR = "ejecutorIa";

	@Bean(name = EJECUTOR)
	TaskExecutor ejecutorIa() {
		ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
		ejecutor.setCorePoolSize(1);
		ejecutor.setMaxPoolSize(2);
		ejecutor.setQueueCapacity(20);
		ejecutor.setThreadNamePrefix("ia-");
		ejecutor.initialize();
		return ejecutor;
	}

}
