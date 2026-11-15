package com.vozpublica.vozpublica.moderacao;

public enum MotivoSinalizacao {
    SPAM("Spam ou propaganda"),
    OFENSIVO("Ofensivo ou discriminatório"),
    FALSO("Informação falsa"),
    DADOS_PESSOAIS("Expõe dados pessoais"),
    OUTRO("Outro motivo");

    private final String rotulo;

    MotivoSinalizacao(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }
}