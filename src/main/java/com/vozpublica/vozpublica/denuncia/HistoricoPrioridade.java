package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "historico_prioridade")
public class HistoricoPrioridade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "denuncia_id", nullable = false, updatable = false)
    private Long denunciaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade_anterior", updatable = false, length = 20)
    private Prioridade prioridadeAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade_nova", nullable = false, updatable = false, length = 20)
    private Prioridade prioridadeNova;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false, updatable = false)
    private User responsavel;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "data_alteracao", nullable = false, updatable = false)
    private LocalDateTime dataAlteracao = LocalDateTime.now();

    protected HistoricoPrioridade() {}

    public HistoricoPrioridade(Long denunciaId, Prioridade anterior, Prioridade nova,
                               User responsavel, String observacao) {
        this.denunciaId = denunciaId;
        this.prioridadeAnterior = anterior;
        this.prioridadeNova = nova;
        this.responsavel = responsavel;
        this.observacao = observacao;
    }

    public Long getId() { return id; }
    public Long getDenunciaId() { return denunciaId; }
    public Prioridade getPrioridadeAnterior() { return prioridadeAnterior; }
    public Prioridade getPrioridadeNova() { return prioridadeNova; }
    public User getResponsavel() { return responsavel; }
    public String getObservacao() { return observacao; }
    public LocalDateTime getDataAlteracao() { return dataAlteracao; }
}