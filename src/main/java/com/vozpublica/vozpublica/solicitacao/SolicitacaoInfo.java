package com.vozpublica.vozpublica.solicitacao;

import com.vozpublica.vozpublica.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "solicitacoes_info")
public class SolicitacaoInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "denuncia_id", nullable = false, updatable = false)
    private Long denunciaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false, updatable = false)
    private User solicitante;

    @Column(nullable = false, updatable = false, length = 1000)
    private String mensagem;

    @Column(name = "data_solicitacao", nullable = false, updatable = false)
    private LocalDateTime dataSolicitacao = LocalDateTime.now();

    @Column(length = 2000)
    private String resposta;

    @Column(name = "data_resposta")
    private LocalDateTime dataResposta;

    protected SolicitacaoInfo() {}

    public SolicitacaoInfo(Long denunciaId, User solicitante, String mensagem) {
        this.denunciaId = denunciaId;
        this.solicitante = solicitante;
        this.mensagem = mensagem;
    }

    public boolean isAberta() { return resposta == null; }

    public Long getId() { return id; }
    public Long getDenunciaId() { return denunciaId; }
    public User getSolicitante() { return solicitante; }
    public String getMensagem() { return mensagem; }
    public LocalDateTime getDataSolicitacao() { return dataSolicitacao; }
    public String getResposta() { return resposta; }
    public LocalDateTime getDataResposta() { return dataResposta; }
}