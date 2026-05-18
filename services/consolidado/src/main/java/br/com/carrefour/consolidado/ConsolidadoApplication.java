package br.com.carrefour.consolidado;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConsolidadoApplication {

	@NaoTestablePorDesign(motivo = "Ponto de entrada Spring Boot — testado indiretamente pelo @SpringBootTest de contexto")
	public static void main(String[] args) {
		SpringApplication.run(ConsolidadoApplication.class, args);
	}

}
