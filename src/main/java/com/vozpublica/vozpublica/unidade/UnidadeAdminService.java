package com.vozpublica.vozpublica.unidade;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnidadeAdminService {

    private final UnidadeRepository unidadeRepository;

    public UnidadeAdminService(UnidadeRepository unidadeRepository) {
        this.unidadeRepository = unidadeRepository;
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public Unidade criar(String nome, TipoUnidade tipo, Long paiId) {
        if (tipo == null) {
            throw new UnidadeInvalidaException("Escolha o tipo da unidade.");
        }
        String limpo = nome == null ? "" : nome.trim();
        if (limpo.isEmpty()) {
            throw new UnidadeInvalidaException("Informe o nome da unidade.");
        }
        if (limpo.length() > 120) {
            throw new UnidadeInvalidaException("O nome deve ter no máximo 120 caracteres.");
        }

        Unidade pai = null;
        if (tipo.exigePai()) {
            if (paiId == null) {
                throw new UnidadeInvalidaException("Esse tipo precisa de uma unidade superior.");
            }
            pai = unidadeRepository.findById(paiId).filter(Unidade::isAtiva)
                    .orElseThrow(() -> new UnidadeInvalidaException("Unidade superior inválida ou desativada."));
            if (!tipo.paisPermitidos().contains(pai.getTipo())) {
                throw new UnidadeInvalidaException(
                        tipo.getRotulo() + " não pode ficar abaixo de " + pai.getTipo().getRotulo() + ".");
            }
        }

        boolean existe = pai == null
                ? unidadeRepository.existsByPaiIsNullAndNomeIgnoreCase(limpo)
                : unidadeRepository.existsByPaiIdAndNomeIgnoreCase(pai.getId(), limpo);
        if (existe) {
            throw new UnidadeInvalidaException("Já existe uma unidade com esse nome nesse local.");
        }

        try {
            return unidadeRepository.saveAndFlush(new Unidade(limpo, tipo, pai));
        } catch (DataIntegrityViolationException e) {
            // duas requisições simultâneas: o índice único do banco barra a segunda
            throw new UnidadeInvalidaException("Já existe uma unidade com esse nome nesse local.");
        }
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public void alternarAtiva(Long id) {
        unidadeRepository.findById(id)
                .orElseThrow(() -> new UnidadeInvalidaException("Unidade não encontrada."))
                .alternarAtiva();
    }
}