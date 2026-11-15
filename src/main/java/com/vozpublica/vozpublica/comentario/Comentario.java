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

    @Column(nullable = false)
    private boolean oculto = false;

    protected Comentario() {}

    public Comentario(Long denunciaId, User autor, String conteudo) {
        this.denunciaId = denunciaId;
        this.autor = autor;
        this.conteudo = conteudo;
    }

    public boolean isOculto() { return oculto; }

    /** Uso restrito ao ModeracaoService, que é protegido por @PreAuthorize. */
    public void definirOculto(boolean oculto) { this.oculto = oculto; }

    public Long getId() { return id; }
    public Long getDenunciaId() { return denunciaId; }
    public User getAutor() { return autor; }
    public String getConteudo() { return conteudo; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}