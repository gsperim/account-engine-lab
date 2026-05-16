package br.com.carrefour.lancamentos.adapter.out.persistence;

import br.com.carrefour.lancamentos.domain.model.Lancamento;
import br.com.carrefour.lancamentos.domain.model.LancamentoId;
import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import br.com.carrefour.lancamentos.domain.model.Valor;
import br.com.carrefour.lancamentos.domain.port.out.LancamentoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class LancamentoRepositoryAdapter implements LancamentoRepository {

    private final LancamentoJpaRepository jpaRepo;

    public LancamentoRepositoryAdapter(LancamentoJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public Lancamento salvar(Lancamento lancamento) {
        return toDomain(jpaRepo.save(toEntity(lancamento)));
    }

    @Override
    public Optional<Lancamento> buscarPorId(LancamentoId id) {
        return jpaRepo.findById(id.toUUID()).map(this::toDomain);
    }

    @Override
    public List<Lancamento> buscarPorDataCompetencia(LocalDate data, TipoLancamento tipo, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("criadoEm").descending());
        var resultado = tipo != null
                ? jpaRepo.findByDataCompetenciaAndTipo(data, tipo, pageable)
                : jpaRepo.findByDataCompetencia(data, pageable);
        return resultado.stream().map(this::toDomain).toList();
    }

    @Override
    public long contarPorDataCompetencia(LocalDate data, TipoLancamento tipo) {
        return tipo != null
                ? jpaRepo.countByDataCompetenciaAndTipo(data, tipo)
                : jpaRepo.countByDataCompetencia(data);
    }

    @Override
    public boolean existePorId(LancamentoId id) {
        return jpaRepo.existsById(id.toUUID());
    }

    @Override
    public java.math.BigDecimal somarValorPorDataETipo(LocalDate data, TipoLancamento tipo) {
        return jpaRepo.sumValorByDataCompetenciaAndTipo(data, tipo);
    }

    private LancamentoJpaEntity toEntity(Lancamento l) {
        var e = new LancamentoJpaEntity();
        e.setId(l.getId().toUUID());
        e.setTipo(l.getTipo());
        e.setValor(l.getValor().toBigDecimal());
        e.setDescricao(l.getDescricao());
        e.setDataCompetencia(l.getDataCompetencia());
        e.setOperadorId(l.getOperadorId());
        e.setCriadoEm(l.getCriadoEm());
        e.setPayloadHash(l.getPayloadHash());
        e.setEstornado(l.isEstornado());
        e.setEstornadoEm(l.getEstornadoEm());
        return e;
    }

    private Lancamento toDomain(LancamentoJpaEntity e) {
        return Lancamento.reconstituir(
                LancamentoId.de(e.getId()),
                e.getTipo(),
                Valor.de(e.getValor()),
                e.getDescricao(),
                e.getDataCompetencia(),
                e.getOperadorId(),
                e.getCriadoEm(),
                e.getPayloadHash(),
                e.isEstornado(),
                e.getEstornadoEm());
    }
}
