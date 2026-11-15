package com.vozpublica.vozpublica.auditoria;

import com.vozpublica.vozpublica.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false, updatable = false)
    private User responsavel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private AcaoAuditoria acao;

    @Column(nullable = false, updatable = false, length = 30)
    private String entidade;

    @Column(name = "entidade_id", updatable = false)
    private Long entidadeId;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String justificativa;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String detalhes;

    @Column(name = "data_acao", nullable = false, updatable = false)
    private LocalDateTime dataAcao = LocalDateTime.now();

    protected RegistroAuditoria() {}

    public RegistroAuditoria(User responsavel, AcaoAuditoria acao, String entidade,
                             Long entidadeId, String justificativa, String detalhes) {
        this.responsavel = responsavel;
        this.acao = acao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.justificativa = justificativa;
        this.detalhes = detalhes;
    }

    public Long getId() { return id; }
    public User getResponsavel() { return responsavel; }
    public AcaoAuditoria getAcao() { return acao; }
    public String getEntidade() { return entidade; }
    public Long getEntidadeId() { return entidadeId; }
    public String getJustificativa() { return justificativa; }
    public String getDetalhes() { return detalhes; }
    public LocalDateTime getDataAcao() { return dataAcao; }
}