package com.vozpublica.vozpublica.moderacao;

public enum TipoConteudo {
    DENUNCIA("Publicação"),
    COMENTARIO("Comentário");

    private final String rotulo;

    TipoConteudo(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }
}