package com.vozpublica.vozpublica.denuncia;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HistoricoStatusRepository extends JpaRepository<HistoricoStatus, Long> {

    @EntityGraph(attributePaths = "responsavel")
    List<HistoricoStatus> findByDenunciaIdOrderByDataAlteracaoAsc(Long denunciaId);

    /** Data da última mudança para a situação informada, ou null se nunca houve. */
    @Query("SELECT MAX(h.dataAlteracao) FROM HistoricoStatus h "
            + "WHERE h.denuncia.id = :id AND h.statusNovo = :status")
    LocalDateTime ultimaDataPara(@Param("id") Long denunciaId, @Param("status") StatusDenuncia status);
}