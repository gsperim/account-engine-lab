package br.com.carrefour.lancamentos.domain.model;

import java.util.Map;
import java.util.UUID;

public record AuditEvento(
        String operadorId,
        String acao,
        UUID   recursoId,
        Map<String, String> contexto
) {}
