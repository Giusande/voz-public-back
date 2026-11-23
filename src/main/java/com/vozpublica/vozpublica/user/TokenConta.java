package com.vozpublica.vozpublica.user;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tokens_conta")
public class TokenConta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TipoToken tipo;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em", nullable = false, updatable = false)
    private LocalDateTime expiraEm;

    @Column(name = "usado_em")
    private LocalDateTime usadoEm;

    protected TokenConta() {}

    public TokenConta(Long usuarioId, TipoToken tipo, String tokenHash,
                      LocalDateTime criadoEm, LocalDateTime expiraEm) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.tokenHash = tokenHash;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public TipoToken getTipo() { return tipo; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public LocalDateTime getUsadoEm() { return usadoEm; }
}