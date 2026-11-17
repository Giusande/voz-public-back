package com.vozpublica.vozpublica.confirmacao;

public enum TipoConfirmacao {
    TAMBEM_EXISTE("Também existe no meu entorno"),
    CONTINUA_PRESENTE("Continua presente"),
    APARENTEMENTE_RESOLVIDO("Aparentemente foi resolvido");

    private final String rotulo;

    TipoConfirmacao(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }
}