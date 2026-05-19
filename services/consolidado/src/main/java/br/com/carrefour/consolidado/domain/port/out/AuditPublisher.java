package br.com.carrefour.consolidado.domain.port.out;

import br.com.carrefour.consolidado.domain.model.AuditEvento;

public interface AuditPublisher {
    void registrar(AuditEvento evento);
}
