package br.com.carrefour.lancamentos.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, UUID> {

    @Query("SELECT o FROM OutboxJpaEntity o WHERE o.publicado = false ORDER BY o.criadoEm ASC LIMIT 100")
    List<OutboxJpaEntity> buscarPendentes();
}
