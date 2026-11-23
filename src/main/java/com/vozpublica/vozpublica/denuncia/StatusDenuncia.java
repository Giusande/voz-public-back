package com.vozpublica.vozpublica.denuncia;

import java.util.EnumSet;
import java.util.Set;

public enum StatusDenuncia {
    RECEBIDA("Recebida", "text-bg-secondary"),
    EM_ANALISE("Em análise", "text-bg-info"),
    ENCAMINHADA("Encaminhada", "text-bg-primary"),
    EM_EXECUCAO("Em execução", "text-bg-warning"),
    RESOLVIDA("Resolvida", "text-bg-success"),
    INDEFERIDA("Indeferida", "text-bg-dark"),
    REABERTA("Reaberta", "text-bg-danger");

    private final String rotulo;
    private final String classeCor;

    StatusDenuncia(String rotulo, String classeCor) {
        this.rotulo = rotulo;
        this.classeCor = classeCor;
    }

    public String getRotulo() { return rotulo; }

    public String getClasseCor() { return classeCor; }

    public Set<StatusDenuncia> proximos() {
        return switch (this) {
            case RECEBIDA -> EnumSet.of(EM_ANALISE, INDEFERIDA);
            case EM_ANALISE -> EnumSet.of(ENCAMINHADA, INDEFERIDA);
            case ENCAMINHADA -> EnumSet.of(EM_EXECUCAO, EM_ANALISE);
            case EM_EXECUCAO -> EnumSet.of(RESOLVIDA, ENCAMINHADA);
            case RESOLVIDA -> EnumSet.of(REABERTA);
            case INDEFERIDA -> EnumSet.of(REABERTA);
            case REABERTA -> EnumSet.of(EM_ANALISE);
        };
    }

    public boolean podeIrPara(StatusDenuncia destino) {
        return proximos().contains(destino);
    }
}