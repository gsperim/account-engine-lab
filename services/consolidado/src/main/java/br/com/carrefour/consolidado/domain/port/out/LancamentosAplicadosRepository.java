package br.com.carrefour.consolidado.domain.port.out;

import java.time.LocalDate;
import java.util.UUID;

public interface LancamentosAplicadosRepository {

    boolean existePorId(UUID lancamentoId);

    void registrar(UUID lancamentoId, LocalDate dataCompetencia);
}
