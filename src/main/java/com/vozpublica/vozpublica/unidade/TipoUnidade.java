package com.vozpublica.vozpublica.unidade;

import java.util.EnumSet;
import java.util.Set;

public enum TipoUnidade {
    SECRETARIA("Secretaria"),
    ORGAO("Órgão público"),
    DEPARTAMENTO("Departamento"),
    EQUIPE("Equipe");

    private final String rotulo;

    TipoUnidade(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }

    public boolean exigePai() {
        return this == DEPARTAMENTO || this == EQUIPE;
    }

    /** Tipos de unidade superior aceitos para este tipo. */
    public Set<TipoUnidade> paisPermitidos() {
        return switch (this) {
            case DEPARTAMENTO -> EnumSet.of(SECRETARIA, ORGAO);
            case EQUIPE -> EnumSet.of(SECRETARIA, ORGAO, DEPARTAMENTO);
            default -> EnumSet.noneOf(TipoUnidade.class);
        };
    }
}