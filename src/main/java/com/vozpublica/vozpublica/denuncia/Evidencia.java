package com.vozpublica.vozpublica.denuncia;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "evidencias")
public class Evidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "historico_id", nullable = false, updatable = false)
    private Long historicoId;

    @Column(name = "caminho_arquivo", nullable = false, updatable = false, length = 500)
    private String caminhoArquivo;

    @Column(name = "data_upload", nullable = false, updatable = false)
    private LocalDateTime dataUpload = LocalDateTime.now();

    protected Evidencia() {}

    public Evidencia(Long historicoId, String caminhoArquivo) {
        this.historicoId = historicoId;
        this.caminhoArquivo = caminhoArquivo;
    }

    public Long getId() { return id; }
    public Long getHistoricoId() { return historicoId; }
    public String getCaminhoArquivo() { return caminhoArquivo; }
    public LocalDateTime getDataUpload() { return dataUpload; }
}