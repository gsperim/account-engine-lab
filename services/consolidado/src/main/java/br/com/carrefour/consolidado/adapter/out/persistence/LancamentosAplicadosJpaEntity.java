package br.com.carrefour.consolidado.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lancamentos_aplicados")
@Getter @Setter @NoArgsConstructor
public class LancamentosAplicadosJpaEntity {

    @Id
    @Column(name = "lancamento_id", nullable = false)
    private UUID lancamentoId;

    @Column(name = "data_competencia", nullable = false)
    private LocalDate dataCompetencia;

    @Column(name = "aplicado_em", nullable = false)
    private OffsetDateTime aplicadoEm;
}
