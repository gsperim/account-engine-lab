package br.com.carrefour.lancamentos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class LancamentosApplication {

	@NaoTestablePorDesign(motivo = "Ponto de entrada Spring Boot — testado indiretamente pelo @SpringBootTest de contexto")
	public static void main(String[] args) {
		SpringApplication.run(LancamentosApplication.class, args);
	}

}
