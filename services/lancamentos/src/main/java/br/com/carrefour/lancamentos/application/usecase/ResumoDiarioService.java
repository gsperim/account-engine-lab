package br.com.carrefour.lancamentos.application.usecase;

import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import br.com.carrefour.lancamentos.domain.port.in.ResumoDiarioUseCase;
import br.com.carrefour.lancamentos.domain.port.out.LancamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ResumoDiarioService implements ResumoDiarioUseCase {

    private final LancamentoRepository repository;

    public ResumoDiarioService(LancamentoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Resultado executar(Query query) {
        var creditos   = repository.somarValorPorDataETipo(query.data(), TipoLancamento.CREDITO);
        var debitos    = repository.somarValorPorDataETipo(query.data(), TipoLancamento.DEBITO);
        var total      = repository.contarPorDataCompetencia(query.data(), null);
        return new Resultado(query.data(),
                creditos  != null ? creditos  : BigDecimal.ZERO,
                debitos   != null ? debitos   : BigDecimal.ZERO,
                total);
    }
}
