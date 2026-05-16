package br.com.carrefour.consolidado.adapter.in.rest;

import br.com.carrefour.consolidado.adapter.in.rest.dto.generated.Erro;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Erro handleIllegalArgument(IllegalArgumentException ex) {
        return new Erro().codigo("ARGUMENTO_INVALIDO").mensagem(ex.getMessage());
    }
}
