package com.vozpublica.vozpublica.moderacao;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sinalizacoes")
public class Sinalizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TipoConteudo tipo;

    @Column(name = "alvo_id", nullable = false, updatable = false)
    private Long alvoId;

    @Column(name = "denuncia_id", nullable = false, updatable = false)
    private Long denunciaId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private MotivoSinalizacao motivo;

    @Column(nullable = false, updatable = false, length = 500)
    private String detalhe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusSinalizacao status;

    @Column(name = "data_criacao", nullable = false, insertable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "analisado_por")
    private Long analisadoPorId;

    @Column(name = "data_analise")
    private LocalDateTime dataAnalise;

    @Column(name = "justificativa_analise", columnDefinition = "TEXT")
    private String justificativaAnalise;

    protected Sinalizacao() {}

    public void encerrar(StatusSinalizacao novo, Long analistaId, String justificativa) {
        this.status = novo;
        this.analisadoPorId = analistaId;
        this.dataAnalise = LocalDateTime.now();
        this.justificativaAnalise = justificativa;
    }

    public Long getId() { return id; }
    public TipoConteudo getTipo() { return tipo; }
    public Long getAlvoId() { return alvoId; }
    public Long getDenunciaId() { return denunciaId; }
    public Long getUsuarioId() { return usuarioId; }
    public MotivoSinalizacao getMotivo() { return motivo; }
    public String getDetalhe() { return detalhe; }
    public StatusSinalizacao getStatus() { return status; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public Long getAnalisadoPorId() { return analisadoPorId; }
    public LocalDateTime getDataAnalise() { return dataAnalise; }
    public String getJustificativaAnalise() { return justificativaAnalise; }
}