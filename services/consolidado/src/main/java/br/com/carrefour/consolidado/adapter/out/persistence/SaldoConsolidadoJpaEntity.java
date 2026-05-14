package br.com.carrefour.consolidado.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "saldo_consolidado")
@Getter @Setter @NoArgsConstructor
public class SaldoConsolidadoJpaEntity {

    @Id
    private LocalDate data;

    @Column(name = "total_creditos", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCreditos;

    @Column(name = "total_debitos", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDebitos;

    @Column(name = "total_lancamentos", nullable = false)
    private int totalLancamentos;

    @Column(name = "ultima_atualizacao", nullable = false)
    private LocalDateTime ultimaAtualizacao;
}
