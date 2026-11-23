package com.vozpublica.vozpublica.solicitacao;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SolicitacaoInfoRepository extends JpaRepository<SolicitacaoInfo, Long> {

    boolean existsByDenunciaIdAndRespostaIsNull(Long denunciaId);

    @EntityGraph(attributePaths = "solicitante")
    List<SolicitacaoInfo> findByDenunciaIdOrderByIdDesc(Long denunciaId);

    List<SolicitacaoInfo> findByDenunciaIdInOrderByIdDesc(Collection<Long> denunciaIds);

    Optional<SolicitacaoInfo> findFirstByDenunciaIdOrderByIdDesc(Long denunciaId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SolicitacaoInfo s SET s.resposta = :resposta, s.dataResposta = :agora "
            + "WHERE s.denunciaId = :denunciaId AND s.resposta IS NULL")
    int responder(@Param("denunciaId") Long denunciaId, @Param("resposta") String resposta,
                  @Param("agora") LocalDateTime agora);
}