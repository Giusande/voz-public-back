package com.vozpublica.vozpublica.confirmacao;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "confirmacoes")
public class Confirmacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "denuncia_id", nullable = false, insertable = false, updatable = false)
    private Long denunciaId;

    @Column(name = "usuario_id", nullable = false, insertable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, insertable = false, updatable = false, length = 30)
    private TipoConfirmacao tipo;

    @Column(name = "data_registro", nullable = false, insertable = false, updatable = false)
    private LocalDateTime dataRegistro;

    protected Confirmacao() {}

    public Long getId() { return id; }
    public Long getDenunciaId() { return denunciaId; }
    public Long getUsuarioId() { return usuarioId; }
    public TipoConfirmacao getTipo() { return tipo; }
    public LocalDateTime getDataRegistro() { return dataRegistro; }
}