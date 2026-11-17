package com.vozpublica.vozpublica.unidade;

import jakarta.persistence.*;

@Entity
@Table(name = "unidades")
public class Unidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoUnidade tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pai_id", updatable = false)
    private Unidade pai;

    @Column(nullable = false)
    private boolean ativa = true;

    protected Unidade() {}

    public Unidade(String nome, TipoUnidade tipo, Unidade pai) {
        this.nome = nome;
        this.tipo = tipo;
        this.pai = pai;
    }

    void alternarAtiva() {
        this.ativa = !this.ativa;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public TipoUnidade getTipo() { return tipo; }
    public Unidade getPai() { return pai; }
    public boolean isAtiva() { return ativa; }
}