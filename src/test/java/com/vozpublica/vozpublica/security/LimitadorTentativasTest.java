package com.vozpublica.vozpublica.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LimitadorTentativasTest {

    static class RelogioMutavel extends Clock {
        private Instant agora = Instant.parse("2026-01-01T10:00:00Z");

        void avancar(Duration d) { agora = agora.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    }

    private static final Duration JANELA = Duration.ofMinutes(15);
    private final RelogioMutavel relogio = new RelogioMutavel();
    private final LimitadorTentativas limitador = new LimitadorTentativas(relogio);

    private void falhar(String chave, int vezes) {
        for (int i = 0; i < vezes; i++) limitador.registrarFalha(chave, 5, JANELA, JANELA);
    }

    @Test
    void bloqueiaNoLimiteELiberaDepoisDoTempoDeBloqueio() {
        falhar("a", 4);
        assertThat(limitador.bloqueado("a")).isFalse();

        falhar("a", 1);
        assertThat(limitador.bloqueado("a")).isTrue();

        relogio.avancar(Duration.ofMinutes(14));
        assertThat(limitador.bloqueado("a")).isTrue();

        relogio.avancar(Duration.ofMinutes(2));
        assertThat(limitador.bloqueado("a")).isFalse();
    }

    @Test
    void falhasForaDaJanelaNaoSeAcumulam() {
        falhar("a", 4);
        relogio.avancar(Duration.ofMinutes(16));
        falhar("a", 4);

        assertThat(limitador.bloqueado("a")).isFalse();
    }

    @Test
    void limparZeraAContagem() {
        falhar("a", 4);
        limitador.limpar("a");
        falhar("a", 4);

        assertThat(limitador.bloqueado("a")).isFalse();
    }

    @Test
    void chavesSaoIndependentes() {
        falhar("a", 5);

        assertThat(limitador.bloqueado("a")).isTrue();
        assertThat(limitador.bloqueado("b")).isFalse();
    }

    @Test
    void permitirContaAsTentativasEReiniciaAJanela() {
        Duration hora = Duration.ofHours(1);
        assertThat(limitador.permitir("x", 3, hora)).isTrue();
        assertThat(limitador.permitir("x", 3, hora)).isTrue();
        assertThat(limitador.permitir("x", 3, hora)).isTrue();
        assertThat(limitador.permitir("x", 3, hora)).isFalse();

        relogio.avancar(Duration.ofMinutes(61));
        assertThat(limitador.permitir("x", 3, hora)).isTrue();
    }
}