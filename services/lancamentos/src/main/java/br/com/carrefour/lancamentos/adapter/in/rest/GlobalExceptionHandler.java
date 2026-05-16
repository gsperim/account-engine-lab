package br.com.carrefour.lancamentos.adapter.in.rest;

import br.com.carrefour.lancamentos.adapter.in.rest.dto.generated.Erro;
import br.com.carrefour.lancamentos.domain.exception.LancamentoConflitanteException;
import br.com.carrefour.lancamentos.domain.exception.LancamentoDuplicadoException;
import br.com.carrefour.lancamentos.domain.exception.LancamentoJaEstornadoException;

import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LancamentoDuplicadoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Erro handleDuplicado(LancamentoDuplicadoException ex) {
        return new Erro().codigo("LANCAMENTO_DUPLICADO").mensagem(ex.getMessage());
    }

    @ExceptionHandler(LancamentoConflitanteException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Erro handleConflitante(LancamentoConflitanteException ex) {
        return new Erro().codigo("IDEMPOTENCY_KEY_CONFLITO").mensagem(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Erro handleValidacao(MethodArgumentNotValidException ex) {
        var mensagem = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Dados inválidos");
        return new Erro().codigo("DADOS_INVALIDOS").mensagem(mensagem);
    }

    @ExceptionHandler(LancamentoJaEstornadoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Erro handleJaEstornado(LancamentoJaEstornadoException ex) {
        return new Erro().codigo("LANCAMENTO_JA_ESTORNADO").mensagem(ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Erro handleNaoEncontrado(NoSuchElementException ex) {
        return new Erro().codigo("NAO_ENCONTRADO").mensagem(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Erro handleIllegalArgument(IllegalArgumentException ex) {
        return new Erro().codigo("ARGUMENTO_INVALIDO").mensagem(ex.getMessage());
    }
}
