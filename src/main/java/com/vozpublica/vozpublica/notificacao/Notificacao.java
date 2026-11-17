package com.vozpublica.vozpublica.notificacao;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notificacoes")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TipoNotificacao tipo;

    @Column(nullable = false, updatable = false, length = 600)
    private String mensagem;

    @Column(name = "denuncia_id", updatable = false)
    private Long denunciaId;

    @Column(nullable = false)
    private boolean lida = false;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao = LocalDateTime.now();

    protected Notificacao() {}

    public Notificacao(Long usuarioId, TipoNotificacao tipo, String mensagem, Long denunciaId) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.mensagem = mensagem;
        this.denunciaId = denunciaId;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public TipoNotificacao getTipo() { return tipo; }
    public String getMensagem() { return mensagem; }
    public Long getDenunciaId() { return denunciaId; }
    public boolean isLida() { return lida; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}