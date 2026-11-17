package com.vozpublica.vozpublica.encaminhamento;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EncaminhamentoRepository extends JpaRepository<Encaminhamento, Long> {

    @EntityGraph(attributePaths = {"unidade", "unidade.pai", "servidor", "responsavel"})
    List<Encaminhamento> findByDenunciaIdOrderByIdDesc(Long denunciaId);

    @EntityGraph(attributePaths = {"unidade", "unidade.pai", "servidor", "responsavel"})
    Optional<Encaminhamento> findFirstByDenunciaIdOrderByIdDesc(Long denunciaId);

    /** O encaminhamento atual (o de maior id) de cada ocorrência da lista, em uma consulta só. */
    @EntityGraph(attributePaths = {"unidade", "unidade.pai"})
    @Query("SELECT e FROM Encaminhamento e WHERE e.denunciaId IN :ids AND e.id = "
            + "(SELECT MAX(x.id) FROM Encaminhamento x WHERE x.denunciaId = e.denunciaId)")
    List<Encaminhamento> atuaisDe(@Param("ids") Collection<Long> ids);
}