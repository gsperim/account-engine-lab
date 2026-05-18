package br.com.carrefour.consolidado.adapter.out.persistence;

import br.com.carrefour.consolidado.domain.port.out.LancamentosAplicadosRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Component
public class LancamentosAplicadosRepositoryAdapter implements LancamentosAplicadosRepository {

    private final LancamentosAplicadosJpaRepository jpaRepo;

    public LancamentosAplicadosRepositoryAdapter(LancamentosAplicadosJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public boolean existePorId(UUID lancamentoId) {
        return jpaRepo.existsById(Objects.requireNonNull(lancamentoId));
    }

    @Override
    public void registrar(UUID lancamentoId, LocalDate dataCompetencia) {
        var entity = new LancamentosAplicadosJpaEntity();
        entity.setLancamentoId(lancamentoId);
        entity.setDataCompetencia(dataCompetencia);
        entity.setAplicadoEm(OffsetDateTime.now());
        jpaRepo.save(entity);
    }
}
