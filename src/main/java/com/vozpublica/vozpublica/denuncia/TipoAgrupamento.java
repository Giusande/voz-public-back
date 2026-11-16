package com.vozpublica.vozpublica.denuncia;

public enum TipoAgrupamento {
    AGRUPADA("Agrupada"),
    DESAGRUPADA("Desagrupada");

    private final String rotulo;

    TipoAgrupamento(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }
}