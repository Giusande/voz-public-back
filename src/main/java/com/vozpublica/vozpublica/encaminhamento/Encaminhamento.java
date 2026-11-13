package com.vozpublica.vozpublica.encaminhamento;

import com.vozpublica.vozpublica.unidade.Unidade;
import com.vozpublica.vozpublica.user.User;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "encaminhamentos")
public class Encaminhamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "denuncia_id", nullable = false, updatable = false)
    private Long denunciaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_id", nullable = false, updatable = false)
    private Unidade unidade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servidor_id", updatable = false)
    private User servidor;

    @Column(name = "prazo_especifico", updatable = false)
    private LocalDate prazoEspecifico;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String observacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false, updatable = false)
    private User responsavel;

    @Column(name = "data_encaminhamento", nullable = false, updatable = false)
    private LocalDateTime dataEncaminhamento = LocalDateTime.now();

    protected Encaminhamento() {}

    public Encaminhamento(Long denunciaId, Unidade unidade, User servidor,
                          LocalDate prazoEspecifico, String observacao, User responsavel) {
        this.denunciaId = denunciaId;
        this.unidade = unidade;
        this.servidor = servidor;
        this.prazoEspecifico = prazoEspecifico;
        this.observacao = observacao;
        this.responsavel = responsavel;
    }

    public Long getId() { return id; }
    public Long getDenunciaId() { return denunciaId; }
    public Unidade getUnidade() { return unidade; }
    public User getServidor() { return servidor; }
    public LocalDate getPrazoEspecifico() { return prazoEspecifico; }
    public String getObservacao() { return observacao; }
    public User getResponsavel() { return responsavel; }
    public LocalDateTime getDataEncaminhamento() { return dataEncaminhamento; }
}