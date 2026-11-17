package com.vozpublica.vozpublica.denuncia;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AgrupamentoRepository extends JpaRepository<Agrupamento, Long> {

    @EntityGraph(attributePaths = "responsavel")
    List<Agrupamento> findByDenunciaIdOrderByIdDesc(Long denunciaId);

    /** Tudo o que envolve a ocorrência, como filha ou como principal. */
    @Query("SELECT a FROM Agrupamento a JOIN FETCH a.responsavel "
            + "WHERE a.denunciaId = :id OR a.principalId = :id ORDER BY a.id DESC")
    List<Agrupamento> historicoDe(@Param("id") Long id);
}