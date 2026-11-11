package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "historico_status")
public class HistoricoStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "denuncia_id", nullable = false, updatable = false)
    private Denuncia denuncia;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", updatable = false, length = 30)
    private StatusDenuncia statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false, updatable = false, length = 30)
    private StatusDenuncia statusNovo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false, updatable = false)
    private User responsavel;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "data_alteracao", nullable = false, updatable = false)
    private LocalDateTime dataAlteracao = LocalDateTime.now();

    protected HistoricoStatus() {}

    public HistoricoStatus(Denuncia denuncia, StatusDenuncia statusAnterior,
                           StatusDenuncia statusNovo, User responsavel, String observacao) {
        this.denuncia = denuncia;
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        this.responsavel = responsavel;
        this.observacao = observacao;
    }

    public Long getId() { return id; }
    public Denuncia getDenuncia() { return denuncia; }
    public StatusDenuncia getStatusAnterior() { return statusAnterior; }
    public StatusDenuncia getStatusNovo() { return statusNovo; }
    public User getResponsavel() { return responsavel; }
    public String getObservacao() { return observacao; }
    public LocalDateTime getDataAlteracao() { return dataAlteracao; }
}
