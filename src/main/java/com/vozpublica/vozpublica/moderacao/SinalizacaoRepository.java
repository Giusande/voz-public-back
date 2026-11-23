package com.vozpublica.vozpublica.moderacao;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SinalizacaoRepository extends JpaRepository<Sinalizacao, Long> {

    @Modifying
    @Query(value = "INSERT INTO sinalizacoes (tipo, alvo_id, denuncia_id, usuario_id, motivo, detalhe) "
            + "VALUES (:tipo, :alvoId, :denunciaId, :usuarioId, :motivo, :detalhe) "
            + "ON CONFLICT (usuario_id, tipo, alvo_id) DO NOTHING", nativeQuery = true)
    int inserir(@Param("tipo") String tipo, @Param("alvoId") Long alvoId,
                @Param("denunciaId") Long denunciaId, @Param("usuarioId") Long usuarioId,
                @Param("motivo") String motivo, @Param("detalhe") String detalhe);

    long countByUsuarioIdAndDataCriacaoAfter(Long usuarioId, LocalDateTime desde);

    Page<Sinalizacao> findByStatusOrderByIdAsc(StatusSinalizacao status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sinalizacao s WHERE s.id = :id")
    Optional<Sinalizacao> buscarParaAtualizar(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Sinalizacao s SET s.status = :novo, s.analisadoPorId = :analista, "
            + "s.dataAnalise = :agora, s.justificativaAnalise = :justificativa "
            + "WHERE s.tipo = :tipo AND s.alvoId = :alvoId AND s.status = :aberta")
    int resolverAbertas(@Param("novo") StatusSinalizacao novo, @Param("analista") Long analista,
                        @Param("agora") LocalDateTime agora, @Param("justificativa") String justificativa,
                        @Param("tipo") TipoConteudo tipo, @Param("alvoId") Long alvoId,
                        @Param("aberta") StatusSinalizacao aberta);

        long countByStatus(StatusSinalizacao status);
}