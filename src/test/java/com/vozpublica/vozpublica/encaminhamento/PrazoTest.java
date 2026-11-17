package com.vozpublica.vozpublica.encaminhamento;

import com.vozpublica.vozpublica.denuncia.StatusDenuncia;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PrazoTest {

    private static final LocalDateTime CRIACAO = LocalDateTime.of(2026, 1, 10, 15, 0);

    @Test
    void prazoDaCategoriaContaApartirDaCriacao() {
        Prazo p = Prazo.calcular(CRIACAO, 15, null, StatusDenuncia.RECEBIDA, LocalDate.of(2026, 1, 11));

        assertThat(p.data()).isEqualTo(LocalDate.of(2026, 1, 25));
        assertThat(p.doEncaminhamento()).isFalse();
        assertThat(p.atrasada()).isFalse();
    }

    @Test
    void noDiaDoPrazoAindaEstaEmDiaEnoDiaSeguinteJaEstaAtrasada() {
        Prazo noDia = Prazo.calcular(CRIACAO, 15, null, StatusDenuncia.EM_ANALISE, LocalDate.of(2026, 1, 25));
        Prazo depois = Prazo.calcular(CRIACAO, 15, null, StatusDenuncia.EM_ANALISE, LocalDate.of(2026, 1, 26));

        assertThat(noDia.atrasada()).isFalse();
        assertThat(depois.atrasada()).isTrue();
        assertThat(depois.diasAtraso()).isEqualTo(1);
    }

    @Test
    void prazoEspecificoDoEncaminhamentoSubstituiODaCategoria() {
        LocalDate especifico = LocalDate.of(2026, 3, 1);
        Prazo p = Prazo.calcular(CRIACAO, 15, especifico, StatusDenuncia.ENCAMINHADA, LocalDate.of(2026, 2, 1));

        assertThat(p.data()).isEqualTo(especifico);
        assertThat(p.doEncaminhamento()).isTrue();
        assertThat(p.atrasada()).isFalse(); // a categoria já teria vencido
    }

    @Test
    void ocorrenciaEncerradaNuncaFicaAtrasada() {
        LocalDate muitoDepois = LocalDate.of(2030, 1, 1);

        assertThat(Prazo.calcular(CRIACAO, 15, null, StatusDenuncia.RESOLVIDA, muitoDepois).atrasada()).isFalse();
        assertThat(Prazo.calcular(CRIACAO, 15, null, StatusDenuncia.INDEFERIDA, muitoDepois).atrasada()).isFalse();
    }

    @Test
    void todasAsSituacoesAbertasPodemFicarAtrasadas() {
        LocalDate muitoDepois = LocalDate.of(2030, 1, 1);

        for (StatusDenuncia s : StatusDenuncia.values()) {
            if (s == StatusDenuncia.RESOLVIDA || s == StatusDenuncia.INDEFERIDA) continue;
            assertThat(Prazo.calcular(CRIACAO, 15, null, s, muitoDepois).atrasada())
                    .as("situação %s", s).isTrue();
        }
    }
}