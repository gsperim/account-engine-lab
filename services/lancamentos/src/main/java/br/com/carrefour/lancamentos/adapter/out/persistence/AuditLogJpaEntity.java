package br.com.carrefour.lancamentos.adapter.out.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLogJpaEntity {

    @Id
    private UUID id;

    @Column(name = "operador_id", nullable = false)
    private String operadorId;

    @Column(nullable = false, length = 100)
    private String acao;

    @Column(name = "recurso_id")
    private UUID recursoId;

    @Column(columnDefinition = "TEXT")
    private String contexto;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    protected AuditLogJpaEntity() {}

    public AuditLogJpaEntity(UUID id, String operadorId, String acao, UUID recursoId, String contexto) {
        this.id          = id;
        this.operadorId  = operadorId;
        this.acao        = acao;
        this.recursoId   = recursoId;
        this.contexto    = contexto;
    }

    public UUID getId()          { return id; }
    public String getOperadorId(){ return operadorId; }
    public String getAcao()      { return acao; }
    public UUID getRecursoId()   { return recursoId; }
    public String getContexto()  { return contexto; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
}
