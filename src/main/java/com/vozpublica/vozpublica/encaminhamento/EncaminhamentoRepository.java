package com.vozpublica.vozpublica.encaminhamento;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EncaminhamentoRepository extends JpaRepository<Encaminhamento, Long> {

    @EntityGraph(attributePaths = {"unidade", "unidade.pai", "servidor", "responsavel"})
    List<Encaminhamento> findByDenunciaIdOrderByIdDesc(Long denunciaId);

    @EntityGraph(attributePaths = {"unidade", "unidade.pai", "servidor", "responsavel"})
    Optional<Encaminhamento> findFirstByDenunciaIdOrderByIdDesc(Long denunciaId);
}