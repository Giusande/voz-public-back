package com.vozpublica.vozpublica.encaminhamento;

import com.vozpublica.vozpublica.denuncia.StatusDenuncia;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PrazoReaberturaPuroTest {

    private static final LocalDateTime REABERTURA = LocalDateTime.of(2026, 3, 1, 15, 0);

    @Test
    void aReaberturaSubstituiACriacaoComoDataBase() {
        Prazo p = Prazo.calcular(REABERTURA, true, 15, null, StatusDenuncia.REABERTA, LocalDate.of(2026, 3, 10));

        assertThat(p.data()).isEqualTo(LocalDate.of(2026, 3, 16));
        assertThat(p.aPartirDaReabertura()).isTrue();
        assertThat(p.doEncaminhamento()).isFalse();
        assertThat(p.atrasada()).isFalse();
    }

    @Test
    void depoisDoNovoPrazoFicaAtrasadaNormalmente() {
        Prazo noDia = Prazo.calcular(REABERTURA, true, 15, null, StatusDenuncia.EM_ANALISE, LocalDate.of(2026, 3, 16));
        Prazo depois = Prazo.calcular(REABERTURA, true, 15, null, StatusDenuncia.EM_ANALISE, LocalDate.of(2026, 3, 17));

        assertThat(noDia.atrasada()).isFalse();
        assertThat(depois.atrasada()).isTrue();
        assertThat(depois.diasAtraso()).isEqualTo(1);
    }

    @Test
    void prazoEspecificoContinuaTendoPrioridadeSobreABaseDaReabertura() {
        LocalDate especifico = LocalDate.of(2026, 4, 30);
        Prazo p = Prazo.calcular(REABERTURA, true, 15, especifico, StatusDenuncia.ENCAMINHADA, LocalDate.of(2026, 4, 1));

        assertThat(p.data()).isEqualTo(especifico);
        assertThat(p.doEncaminhamento()).isTrue();
        assertThat(p.aPartirDaReabertura()).isFalse();
    }
}