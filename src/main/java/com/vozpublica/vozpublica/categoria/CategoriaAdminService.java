package com.vozpublica.vozpublica.categoria;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoriaAdminService {

    private final CategoriaRepository categoriaRepository;

    public CategoriaAdminService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public void alterarPrazo(Long categoriaId, Integer dias) {
        if (dias == null || dias < 1 || dias > 365) {
            throw new CategoriaAdminException("O prazo deve ficar entre 1 e 365 dias.");
        }
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new CategoriaAdminException("Categoria não encontrada."));
        categoria.alterarPrazo(dias);
    }
}