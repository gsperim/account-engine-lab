package br.com.carrefour.consolidado.adapter.in.rest;

import br.com.carrefour.consolidado.adapter.in.rest.dto.generated.Erro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Erro handleIllegalArgument(IllegalArgumentException ex) {
        log.atWarn()
                .addKeyValue("event", "argumento_invalido")
                .log("Argumento inválido na requisição");
        return new Erro().codigo("ARGUMENTO_INVALIDO").mensagem(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Erro handleUnexpected(Exception ex) {
        log.atError()
                .addKeyValue("event", "erro_inesperado")
                .setCause(ex)
                .log("Erro inesperado — não tratado");
        return new Erro().codigo("ERRO_INTERNO").mensagem("Erro interno do servidor");
    }
}
