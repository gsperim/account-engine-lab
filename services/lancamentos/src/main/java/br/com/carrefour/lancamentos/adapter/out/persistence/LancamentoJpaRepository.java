package br.com.carrefour.lancamentos.adapter.out.persistence;

import br.com.carrefour.lancamentos.domain.model.TipoLancamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface LancamentoJpaRepository extends JpaRepository<LancamentoJpaEntity, UUID> {

    Page<LancamentoJpaEntity> findByDataCompetencia(LocalDate data, Pageable pageable);

    Page<LancamentoJpaEntity> findByDataCompetenciaAndTipo(LocalDate data, TipoLancamento tipo, Pageable pageable);

    long countByDataCompetencia(LocalDate data);

    long countByDataCompetenciaAndTipo(LocalDate data, TipoLancamento tipo);

    @Query("SELECT COALESCE(SUM(l.valor), 0) FROM LancamentoJpaEntity l WHERE l.dataCompetencia = :data AND l.tipo = :tipo")
    BigDecimal sumValorByDataCompetenciaAndTipo(@Param("data") LocalDate data, @Param("tipo") TipoLancamento tipo);
}
