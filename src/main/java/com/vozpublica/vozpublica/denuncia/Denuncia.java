package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.categoria.Categoria;
import com.vozpublica.vozpublica.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "denuncias")
public class Denuncia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private User autor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @Column(length = 255)
    private String endereco;

    @Column(nullable = false, length = 100)
    private String bairro;

    @Column(nullable = false, length = 50)
    private String regiao;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade_sugerida", nullable = false, length = 20)
    private Prioridade prioridadeSugerida;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade_oficial", length = 20)
    private Prioridade prioridadeOficial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusDenuncia status = StatusDenuncia.RECEBIDA;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao = LocalDateTime.now();

    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao = LocalDateTime.now();

    protected Denuncia() {}

    public Denuncia(User autor, Categoria categoria, String titulo, String descricao,
                    String endereco, String bairro, String regiao,
                    BigDecimal latitude, BigDecimal longitude, Prioridade prioridadeSugerida) {
        this.autor = autor;
        this.categoria = categoria;
        this.titulo = titulo;
        this.descricao = descricao;
        this.endereco = endereco;
        this.bairro = bairro;
        this.regiao = regiao;
        this.latitude = latitude;
        this.longitude = longitude;
        this.prioridadeSugerida = prioridadeSugerida;
    }

    @PreUpdate
    void aoAtualizar() {
        this.dataAtualizacao = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getAutor() { return autor; }
    public Categoria getCategoria() { return categoria; }
    public String getTitulo() { return titulo; }
    public String getDescricao() { return descricao; }
    public String getEndereco() { return endereco; }
    public String getBairro() { return bairro; }
    public String getRegiao() { return regiao; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public Prioridade getPrioridadeSugerida() { return prioridadeSugerida; }
    public Prioridade getPrioridadeOficial() { return prioridadeOficial; }
    public StatusDenuncia getStatus() { return status; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
}