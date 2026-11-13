package com.vozpublica.vozpublica.denuncia;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HistoricoStatusRepository extends JpaRepository<HistoricoStatus, Long> {

    @EntityGraph(attributePaths = "responsavel")
    List<HistoricoStatus> findByDenunciaIdOrderByDataAlteracaoAsc(Long denunciaId);
}