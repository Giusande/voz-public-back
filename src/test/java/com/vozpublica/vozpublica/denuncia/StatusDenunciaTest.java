package com.vozpublica.vozpublica.denuncia;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static com.vozpublica.vozpublica.denuncia.StatusDenuncia.*;
import static org.assertj.core.api.Assertions.assertThat;

class StatusDenunciaTest {

    @Test
    void tabelaDeTransicoesEstaComoCombinado() {
        assertThat(RECEBIDA.proximos()).containsExactlyInAnyOrder(EM_ANALISE, INDEFERIDA);
        assertThat(EM_ANALISE.proximos()).containsExactlyInAnyOrder(ENCAMINHADA, INDEFERIDA);
        assertThat(ENCAMINHADA.proximos()).containsExactlyInAnyOrder(EM_EXECUCAO, EM_ANALISE);
        assertThat(EM_EXECUCAO.proximos()).containsExactlyInAnyOrder(RESOLVIDA, ENCAMINHADA);
        assertThat(RESOLVIDA.proximos()).containsExactly(REABERTA);
        assertThat(INDEFERIDA.proximos()).containsExactly(REABERTA);
        assertThat(REABERTA.proximos()).containsExactly(EM_ANALISE);
    }

    @Test
    void ninguemTransitaParaSiMesmoETodoStatusTemSaida() {
        for (StatusDenuncia s : values()) {
            assertThat(s.proximos()).doesNotContain(s).isNotEmpty();
        }
        assertThat(RECEBIDA.podeIrPara(RESOLVIDA)).isFalse();
        assertThat(EnumSet.allOf(StatusDenuncia.class)).hasSize(7);
    }
}