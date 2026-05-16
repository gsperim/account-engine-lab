package br.com.carrefour.lancamentos.domain.port.in;

import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.TipoLancamento;

import java.time.LocalDate;
import java.util.List;

public interface ListarLancamentosUseCase {

    record Query(LocalDate data, TipoLancamento tipo, int page, int size) {}

    record Result(List<Lancamento> items, long total, int page, int size) {
        public int totalPages() {
            return size == 0 ? 0 : (int) Math.ceil((double) total / size);
        }
    }

    Result executar(Query query);
}
