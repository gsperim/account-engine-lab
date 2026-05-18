package br.com.carrefour.consolidado.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface LancamentosAplicadosJpaRepository extends JpaRepository<LancamentosAplicadosJpaEntity, UUID> {
}
