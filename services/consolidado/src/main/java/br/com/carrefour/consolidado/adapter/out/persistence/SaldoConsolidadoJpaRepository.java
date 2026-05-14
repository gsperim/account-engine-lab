package br.com.carrefour.consolidado.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SaldoConsolidadoJpaRepository extends JpaRepository<SaldoConsolidadoJpaEntity, LocalDate> {

    List<SaldoConsolidadoJpaEntity> findByDataBetweenOrderByData(LocalDate inicio, LocalDate fim);
}
