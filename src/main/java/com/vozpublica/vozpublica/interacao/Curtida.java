package com.vozpublica.vozpublica.interacao;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "curtidas")
public class Curtida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "denuncia_id", nullable = false, updatable = false)
    private Long denunciaId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "data_criacao", nullable = false, insertable = false, updatable = false)
    private LocalDateTime dataCriacao;

    protected Curtida() {}

    public Long getId() { return id; }
    public Long getDenunciaId() { return denunciaId; }
    public Long getUsuarioId() { return usuarioId; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}