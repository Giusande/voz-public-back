package com.vozpublica.vozpublica.denuncia;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HistoricoPrioridadeRepository extends JpaRepository<HistoricoPrioridade, Long> {

    @EntityGraph(attributePaths = "responsavel")
    List<HistoricoPrioridade> findByDenunciaIdOrderByDataAlteracaoAsc(Long denunciaId);
}