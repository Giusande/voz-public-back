package com.vozpublica.vozpublica.denuncia;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fotos")
public class Foto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "denuncia_id", nullable = false, updatable = false)
    private Denuncia denuncia;

    @Column(name = "caminho_arquivo", nullable = false, updatable = false, length = 500)
    private String caminhoArquivo;

    @Column(name = "data_upload", nullable = false, updatable = false)
    private LocalDateTime dataUpload = LocalDateTime.now();

    protected Foto() {}

    public Foto(Denuncia denuncia, String caminhoArquivo) {
        this.denuncia = denuncia;
        this.caminhoArquivo = caminhoArquivo;
    }

    public Long getId() { return id; }
    public Denuncia getDenuncia() { return denuncia; }
    public String getCaminhoArquivo() { return caminhoArquivo; }
    public LocalDateTime getDataUpload() { return dataUpload; }
}