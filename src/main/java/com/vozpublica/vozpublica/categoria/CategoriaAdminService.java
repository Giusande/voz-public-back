package com.vozpublica.vozpublica.categoria;

import com.vozpublica.vozpublica.auditoria.AcaoAuditoria;
import com.vozpublica.vozpublica.auditoria.AuditoriaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoriaAdminService {

    private final CategoriaRepository categoriaRepository;
    private final AuditoriaService auditoria;

    public CategoriaAdminService(CategoriaRepository categoriaRepository, AuditoriaService auditoria) {
        this.categoriaRepository = categoriaRepository;
        this.auditoria = auditoria;
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public void alterarPrazo(Long categoriaId, Integer dias, String emailResponsavel) {
        if (dias == null || dias < 1 || dias > 365) {
            throw new CategoriaAdminException("O prazo deve ficar entre 1 e 365 dias.");
        }
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new CategoriaAdminException("Categoria não encontrada."));

        int anterior = categoria.getPrazoReferenciaDias();
        if (anterior == dias) {
            return; // nada mudou, nada a auditar
        }
        categoria.alterarPrazo(dias);
        auditoria.registrar(emailResponsavel, AcaoAuditoria.PRAZO_CATEGORIA_ALTERADO, "CATEGORIA",
                categoria.getId(), null,
                "Categoria \"" + categoria.getNome() + "\": " + anterior + " → " + dias + " dias");
    }
}