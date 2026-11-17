package com.vozpublica.vozpublica.comentario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ComentarioForm {

    @NotBlank(message = "Escreva um comentário.")
    @Size(max = 1000, message = "O comentário deve ter no máximo 1000 caracteres.")
    private String conteudo;

    public String getConteudo() { return conteudo; }
    public void setConteudo(String conteudo) { this.conteudo = conteudo; }
}