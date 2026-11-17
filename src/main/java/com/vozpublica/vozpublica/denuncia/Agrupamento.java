package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "agrupamentos")
public class Agrupamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "denuncia_id", nullable = false, updatable = false)
    private Long denunciaId;

    @Column(name = "principal_id", nullable = false, updatable = false)
    private Long principalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TipoAgrupamento tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false, updatable = false)
    private User responsavel;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String justificativa;

    @Column(name = "data_acao", nullable = false, updatable = false)
    private LocalDateTime dataAcao = LocalDateTime.now();

    protected Agrupamento() {}

    public Agrupamento(Long denunciaId, Long principalId, TipoAgrupamento tipo,
                       User responsavel, String justificativa) {
        this.denunciaId = denunciaId;
        this.principalId = principalId;
        this.tipo = tipo;
        this.responsavel = responsavel;
        this.justificativa = justificativa;
    }

    public Long getId() { return id; }
    public Long getDenunciaId() { return denunciaId; }
    public Long getPrincipalId() { return principalId; }
    public TipoAgrupamento getTipo() { return tipo; }
    public User getResponsavel() { return responsavel; }
    public String getJustificativa() { return justificativa; }
    public LocalDateTime getDataAcao() { return dataAcao; }
}