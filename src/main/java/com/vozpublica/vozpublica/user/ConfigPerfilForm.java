package com.vozpublica.vozpublica.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ConfigPerfilForm {

    @Size(max = 60, message = "O nome público deve ter no máximo 60 caracteres.")
    @Pattern(regexp = "^[^\\p{Cntrl}\\p{Cf}]*$",
             message = "O nome público não pode ter caracteres de controle ou invisíveis.")
    private String nomePublico;

    private boolean perfilPublico = true;
    private boolean exibirDataCadastro;
    private boolean exibirApoios;

    public String getNomePublico() { return nomePublico; }
    public void setNomePublico(String nomePublico) { this.nomePublico = nomePublico; }
    public boolean isPerfilPublico() { return perfilPublico; }
    public void setPerfilPublico(boolean perfilPublico) { this.perfilPublico = perfilPublico; }
    public boolean isExibirDataCadastro() { return exibirDataCadastro; }
    public void setExibirDataCadastro(boolean exibirDataCadastro) { this.exibirDataCadastro = exibirDataCadastro; }
    public boolean isExibirApoios() { return exibirApoios; }
    public void setExibirApoios(boolean exibirApoios) { this.exibirApoios = exibirApoios; }
}