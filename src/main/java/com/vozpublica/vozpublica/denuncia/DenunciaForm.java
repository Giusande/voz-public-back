package com.vozpublica.vozpublica.denuncia;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class DenunciaForm {

    @NotBlank(message = "Informe um título.")
    @Size(max = 150, message = "O título deve ter no máximo 150 caracteres.")
    private String titulo;

    @NotBlank(message = "Descreva o problema.")
    @Size(min = 10, max = 5000, message = "A descrição deve ter entre 10 e 5000 caracteres.")
    private String descricao;

    @NotNull(message = "Escolha uma categoria.")
    private Long categoriaId;

    @Size(max = 255, message = "O endereço deve ter no máximo 255 caracteres.")
    private String endereco;

    @NotBlank(message = "Informe o bairro.")
    @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres.")
    private String bairro;

    @NotBlank(message = "Escolha a região.")
    @Pattern(regexp = "RPA [1-6] - (Centro|Norte|Noroeste|Oeste|Sudoeste|Sul)",
             message = "Região inválida.")
    private String regiao;

    @DecimalMin(value = "-90.0", message = "Latitude inválida.")
    @DecimalMax(value = "90.0", message = "Latitude inválida.")
    @Digits(integer = 2, fraction = 8, message = "Latitude inválida.")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "Longitude inválida.")
    @DecimalMax(value = "180.0", message = "Longitude inválida.")
    @Digits(integer = 3, fraction = 8, message = "Longitude inválida.")
    private BigDecimal longitude;

    @NotNull(message = "Escolha a prioridade sugerida.")
    private Prioridade prioridadeSugerida;

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }
    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }
    public String getRegiao() { return regiao; }
    public void setRegiao(String regiao) { this.regiao = regiao; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public Prioridade getPrioridadeSugerida() { return prioridadeSugerida; }
    public void setPrioridadeSugerida(Prioridade prioridadeSugerida) { this.prioridadeSugerida = prioridadeSugerida; }
}