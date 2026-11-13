package com.vozpublica.vozpublica.unidade;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnidadeRepository extends JpaRepository<Unidade, Long> {

    @EntityGraph(attributePaths = "pai")
    List<Unidade> findByAtivaTrueOrderByNome();

    @EntityGraph(attributePaths = "pai")
    List<Unidade> findAllByOrderByNomeAsc();

    boolean existsByPaiIdAndNomeIgnoreCase(Long paiId, String nome);

    boolean existsByPaiIsNullAndNomeIgnoreCase(String nome);
}