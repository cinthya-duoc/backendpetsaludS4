package com.petsalud.backendpetsalud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendpetsaludApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendpetsaludApplication.class, args);
	}

}
