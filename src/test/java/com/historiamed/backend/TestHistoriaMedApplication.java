package com.historiamed.backend;

import org.springframework.boot.SpringApplication;

public class TestHistoriaMedApplication {

	public static void main(String[] args) {
		SpringApplication.from(HistoriaMedApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
