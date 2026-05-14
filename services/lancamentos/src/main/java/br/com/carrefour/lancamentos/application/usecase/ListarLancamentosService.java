package br.com.carrefour.lancamentos.application.usecase;

import br.com.carrefour.lancamentos.domain.port.in.ListarLancamentosUseCase;
import br.com.carrefour.lancamentos.domain.port.out.LancamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarLancamentosService implements ListarLancamentosUseCase {

    private final LancamentoRepository repository;

    public ListarLancamentosService(LancamentoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Result executar(Query query) {
        var items = repository.buscarPorDataCompetencia(
                query.data(), query.tipo(), query.page(), query.size());
        var total = repository.contarPorDataCompetencia(query.data(), query.tipo());
        return new Result(items, total, query.page(), query.size());
    }
}
