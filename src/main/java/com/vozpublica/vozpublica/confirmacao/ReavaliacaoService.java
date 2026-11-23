package com.vozpublica.vozpublica.confirmacao;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReavaliacaoService {

    public static final long MINIMO = 3;

    private final ReavaliacaoRepository repository;

    public ReavaliacaoService(ReavaliacaoRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public List<DenunciaDivergente> listar() {
        return repository.listar(MINIMO);
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public boolean divergente(Long denunciaId) {
        return !repository.deUma(denunciaId, MINIMO).isEmpty();
    }
}