package com.vozpublica.vozpublica.comentario;

import com.vozpublica.vozpublica.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comentarios")
public class Comentario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "denuncia_id", nullable = false, updatable = false)
    private Long denunciaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private User autor;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String conteudo;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao = LocalDateTime.now();

    protected Comentario() {}

    public Comentario(Long denunciaId, User autor, String conteudo) {
        this.denunciaId = denunciaId;
        this.autor = autor;
        this.conteudo = conteudo;
    }

    public Long getId() { return id; }
    public Long getDenunciaId() { return denunciaId; }
    public User getAutor() { return autor; }
    public String getConteudo() { return conteudo; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}