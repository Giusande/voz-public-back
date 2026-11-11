package com.vozpublica.vozpublica.categoria;

import jakarta.persistence.*;

@Entity
@Table(name = "categorias")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nome;

    @Column(name = "prazo_referencia_dias", nullable = false)
    private int prazoReferenciaDias;

    @Column(nullable = false)
    private boolean ativa = true;

    protected Categoria() {}

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public int getPrazoReferenciaDias() { return prazoReferenciaDias; }
    public boolean isAtiva() { return ativa; }
}
