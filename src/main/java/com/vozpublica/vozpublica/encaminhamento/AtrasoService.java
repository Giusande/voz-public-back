package com.vozpublica.vozpublica.encaminhamento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class AtrasoService {

    public static final int TAMANHO_PAGINA = 20;

    public record LinhaAtrasada(Long id, String titulo, String bairro, String categoria,
                                String status, LocalDate prazo, long diasAtraso, String unidade) {}

    private final AtrasoRepository atrasoRepository;

    public AtrasoService(AtrasoRepository atrasoRepository) {
        this.atrasoRepository = atrasoRepository;
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public Page<LinhaAtrasada> listar(int pagina) {
        LocalDate hoje = LocalDate.now();
        return atrasoRepository
                .buscarAtrasadas(hoje, PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA))
                .map(v -> {
                    LocalDate prazo = LocalDate.parse(v.getPrazo());
                    return new LinhaAtrasada(v.getId(), v.getTitulo(), v.getBairro(), v.getCategoria(),
                            v.getStatus(), prazo, ChronoUnit.DAYS.between(prazo, hoje), v.getUnidade());
                });
    }
}