package br.com.carrefour.lancamentos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class LancamentosApplication {

	public static void main(String[] args) {
		SpringApplication.run(LancamentosApplication.class, args);
	}

}
