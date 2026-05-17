package br.com.carrefour.lancamentos.adapter.in.rest;

import br.com.carrefour.lancamentos.adapter.in.rest.dto.generated.Erro;
import br.com.carrefour.lancamentos.domain.exception.LancamentoConflitanteException;
import br.com.carrefour.lancamentos.domain.exception.LancamentoDuplicadoException;
import br.com.carrefour.lancamentos.domain.exception.LancamentoJaEstornadoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LancamentoDuplicadoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Erro handleDuplicado(LancamentoDuplicadoException ex) {
        log.atWarn()
                .addKeyValue("event", "lancamento_duplicado")
                .addKeyValue("codigo", "LANCAMENTO_DUPLICADO")
                .log("Requisição rejeitada — lançamento duplicado");
        return new Erro().codigo("LANCAMENTO_DUPLICADO").mensagem(ex.getMessage());
    }

    @ExceptionHandler(LancamentoConflitanteException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Erro handleConflitante(LancamentoConflitanteException ex) {
        log.atWarn()
                .addKeyValue("event", "idempotency_conflito")
                .addKeyValue("codigo", "IDEMPOTENCY_KEY_CONFLITO")
                .log("Requisição rejeitada — conflito de idempotência");
        return new Erro().codigo("IDEMPOTENCY_KEY_CONFLITO").mensagem(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Erro handleValidacao(MethodArgumentNotValidException ex) {
        var mensagem = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Dados inválidos");
        log.atWarn()
                .addKeyValue("event",  "validacao_falhou")
                .addKeyValue("campos", mensagem)
                .log("Requisição rejeitada — dados inválidos");
        return new Erro().codigo("DADOS_INVALIDOS").mensagem(mensagem);
    }

    @ExceptionHandler(LancamentoJaEstornadoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Erro handleJaEstornado(LancamentoJaEstornadoException ex) {
        log.atWarn()
                .addKeyValue("event", "lancamento_ja_estornado")
                .addKeyValue("codigo", "LANCAMENTO_JA_ESTORNADO")
                .log("Requisição rejeitada — lançamento já foi estornado");
        return new Erro().codigo("LANCAMENTO_JA_ESTORNADO").mensagem(ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Erro handleNaoEncontrado(NoSuchElementException ex) {
        log.atWarn()
                .addKeyValue("event", "recurso_nao_encontrado")
                .log("Recurso não encontrado");
        return new Erro().codigo("NAO_ENCONTRADO").mensagem(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Erro handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.atWarn()
                .addKeyValue("event",     "tipo_invalido")
                .addKeyValue("parametro", ex.getName())
                .addKeyValue("valor",     String.valueOf(ex.getValue()))
                .log("Tipo de parâmetro inválido na requisição");
        return new Erro().codigo("TIPO_INVALIDO")
                .mensagem("Parâmetro '" + ex.getName() + "' com valor inválido: " + ex.getValue());
    }

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
