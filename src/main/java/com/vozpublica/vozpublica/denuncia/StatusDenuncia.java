package com.vozpublica.vozpublica.denuncia;

import java.util.EnumSet;
import java.util.Set;

public enum StatusDenuncia {
    RECEBIDA, EM_ANALISE, ENCAMINHADA, EM_EXECUCAO, RESOLVIDA, INDEFERIDA, REABERTA;

    /** Situações para as quais a equipe pode mover a ocorrência a partir desta. */
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