package br.com.carrefour.lancamentos.domain.port.out;

import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.model.TipoLancamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LancamentoRepository {
    Lancamento salvar(Lancamento lancamento);
    Optional<Lancamento> buscarPorId(LancamentoId id);
    // SELECT ... FOR UPDATE — usar quando a leitura precisa ser exclusiva até o commit (ex: estorno)
    Optional<Lancamento> buscarPorIdComLock(LancamentoId id);
    List<Lancamento> buscarPorDataCompetencia(LocalDate data, TipoLancamento tipo, int page, int size);
    long contarPorDataCompetencia(LocalDate data, TipoLancamento tipo);
    boolean existePorId(LancamentoId id);
    BigDecimal somarValorPorDataETipo(LocalDate data, TipoLancamento tipo);
}
