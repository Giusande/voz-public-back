package com.vozpublica.vozpublica.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LimitadorTentativas {

    private static final int MAX_CHAVES = 20_000;

    private static final class Estado {
        int contagem;
        Instant inicio;
        Instant bloqueadoAte;
    }

    private final Map<String, Estado> mapa = new ConcurrentHashMap<>();
    private final Clock clock;

    public LimitadorTentativas(Clock clock) {
        this.clock = clock;
    }

    public boolean bloqueado(String chave) {
        Estado e = mapa.get(chave);
        if (e == null) return false;
        synchronized (e) {
            return e.bloqueadoAte != null && clock.instant().isBefore(e.bloqueadoAte);
        }
    }

    public void registrarFalha(String chave, int limite, Duration janela, Duration bloqueio) {
        Instant agora = clock.instant();
        podarSeCheio(agora);
        Estado e = mapa.computeIfAbsent(chave, k -> new Estado());
        synchronized (e) {
            reiniciarJanelaSeVenceu(e, agora, janela);
            e.contagem++;
            if (e.contagem >= limite) {
                e.bloqueadoAte = agora.plus(bloqueio);
                e.contagem = 0;
                e.inicio = null;
            }
        }
    }

    public boolean permitir(String chave, int limite, Duration janela) {
        Instant agora = clock.instant();
        podarSeCheio(agora);
        Estado e = mapa.computeIfAbsent(chave, k -> new Estado());
        synchronized (e) {
            reiniciarJanelaSeVenceu(e, agora, janela);
            e.contagem++;
            return e.contagem <= limite;
        }
    }

    public void limpar(String chave) {
        mapa.remove(chave);
    }

    private static void reiniciarJanelaSeVenceu(Estado e, Instant agora, Duration janela) {
        if (e.inicio == null || agora.isAfter(e.inicio.plus(janela))) {
            e.inicio = agora;
            e.contagem = 0;
        }
    }

    private void podarSeCheio(Instant agora) {
        if (mapa.size() <= MAX_CHAVES) return;
        mapa.entrySet().removeIf(en -> {
            Estado e = en.getValue();
            synchronized (e) {
                boolean desbloqueado = e.bloqueadoAte == null || agora.isAfter(e.bloqueadoAte);
                boolean antigo = e.inicio == null || agora.isAfter(e.inicio.plus(Duration.ofDays(1)));
                return desbloqueado && antigo;
            }
        });
        if (mapa.size() > MAX_CHAVES) mapa.clear();
    }
}