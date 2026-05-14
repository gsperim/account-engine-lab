package br.com.carrefour.lancamentos.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxJpaEntity {

    @Id
    private UUID id;

    @Column(name = "lancamento_id", nullable = false)
    private UUID lancamentoId;

    @Column(name = "evento_tipo", nullable = false, length = 50)
    private String eventoTipo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    private boolean publicado;

    @Column(name = "publicado_em")
    private LocalDateTime publicadoEm;

    private int tentativas;

    protected OutboxJpaEntity() {}

    public OutboxJpaEntity(UUID id, UUID lancamentoId, String eventoTipo, String payload) {
        this.id          = id;
        this.lancamentoId = lancamentoId;
        this.eventoTipo  = eventoTipo;
        this.payload     = payload;
        this.criadoEm    = LocalDateTime.now();
        this.publicado   = false;
        this.tentativas  = 0;
    }

    public void marcarPublicado() {
        this.publicado   = true;
        this.publicadoEm = LocalDateTime.now();
    }

    public void incrementarTentativas() { this.tentativas++; }

    public UUID getId()           { return id; }
    public UUID getLancamentoId() { return lancamentoId; }
    public String getPayload()    { return payload; }
    public boolean isPublicado()  { return publicado; }
    public int getTentativas()    { return tentativas; }
}
