package com.vozpublica.vozpublica.relatorio;

import com.vozpublica.vozpublica.relatorio.RelatorioService.Painel;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TransparenciaService {

    private static final Duration VALIDADE = Duration.ofSeconds(60);
    private static final int MAX_ENTRADAS = 50;

    private record Entrada(Painel painel, Instant em) {}

    private final Map<String, Entrada> cache = new ConcurrentHashMap<>();
    private final RelatorioService relatorioService;

    public TransparenciaService(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    /** Só o período é aceito: nenhum outro filtro chega à consulta pública. */
    public Painel painel(LocalDate de, LocalDate ate) {
        String chave = de + "|" + ate;
        Instant agora = Instant.now();
        Entrada guardada = cache.get(chave);
        if (guardada != null && Duration.between(guardada.em(), agora).compareTo(VALIDADE) < 0) {
            return guardada.painel();
        }

        FiltroRelatorio filtro = new FiltroRelatorio();
        filtro.setDe(de);
        filtro.setAte(ate);
        Painel calculado = relatorioService.painelPublico(filtro);

        if (cache.size() >= MAX_ENTRADAS) {
            cache.clear();
        }
        cache.put(chave, new Entrada(calculado, agora));
        return calculado;
    }
}