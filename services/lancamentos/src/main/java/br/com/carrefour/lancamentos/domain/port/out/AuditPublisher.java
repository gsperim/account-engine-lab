package br.com.carrefour.lancamentos.domain.port.out;

import br.com.carrefour.lancamentos.domain.model.AuditEvento;

public interface AuditPublisher {
    void registrar(AuditEvento evento);
}
