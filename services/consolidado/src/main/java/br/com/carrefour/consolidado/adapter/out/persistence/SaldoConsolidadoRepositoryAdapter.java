package br.com.carrefour.consolidado.adapter.out.persistence;

import br.com.carrefour.consolidado.domain.model.SaldoConsolidado;
import br.com.carrefour.consolidado.domain.port.out.SaldoConsolidadoRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class SaldoConsolidadoRepositoryAdapter implements SaldoConsolidadoRepository {

    private final SaldoConsolidadoJpaRepository jpaRepo;

    public SaldoConsolidadoRepositoryAdapter(SaldoConsolidadoJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public Optional<SaldoConsolidado> buscarPorData(LocalDate data) {
        return Optional.ofNullable(buscarDoCache(data));
    }

    // Cache opera sobre SaldoConsolidado (não Optional) — evita problema de serialização do wrapper
    // unless="#result == null" impede que cache miss (null) seja armazenado
    @Cacheable(value = "saldo-consolidado", key = "#data", unless = "#result == null")
    SaldoConsolidado buscarDoCache(LocalDate data) {
        return jpaRepo.findById(data).map(this::toDomain).orElse(null);
    }

    @Override
    public List<SaldoConsolidado> buscarPorPeriodo(LocalDate inicio, LocalDate fim) {
        return jpaRepo.findByDataBetweenOrderByData(inicio, fim).stream()
                .map(this::toDomain).toList();
    }

    @Override
    @CacheEvict(value = "saldo-consolidado", key = "#saldo.data")
    public SaldoConsolidado salvar(SaldoConsolidado saldo) {
        return toDomain(jpaRepo.save(toEntity(saldo)));
    }

    private SaldoConsolidadoJpaEntity toEntity(SaldoConsolidado s) {
        var e = new SaldoConsolidadoJpaEntity();
        e.setData(s.getData());
        e.setTotalCreditos(s.getTotalCreditos());
        e.setTotalDebitos(s.getTotalDebitos());
        e.setTotalLancamentos(s.getTotalLancamentos());
        e.setUltimaAtualizacao(s.getUltimaAtualizacao());
        return e;
    }

    private SaldoConsolidado toDomain(SaldoConsolidadoJpaEntity e) {
        return SaldoConsolidado.reconstituir(
                e.getData(), e.getTotalCreditos(), e.getTotalDebitos(),
                e.getTotalLancamentos(), e.getUltimaAtualizacao());
    }
}
