package com.vozpublica.vozpublica.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TokenContaRepository extends JpaRepository<TokenConta, Long> {

    Optional<TokenConta> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE TokenConta t SET t.usadoEm = :agora "
            + "WHERE t.usuarioId = :usuarioId AND t.tipo = :tipo AND t.usadoEm IS NULL")
    int invalidarPendentes(@Param("usuarioId") Long usuarioId, @Param("tipo") TipoToken tipo,
                           @Param("agora") LocalDateTime agora);

    /** Atômico: de duas requisições simultâneas com o mesmo token, só uma recebe 1. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE TokenConta t SET t.usadoEm = :agora "
            + "WHERE t.tokenHash = :hash AND t.tipo = :tipo AND t.usadoEm IS NULL AND t.expiraEm > :agora")
    int consumir(@Param("hash") String hash, @Param("tipo") TipoToken tipo,
                 @Param("agora") LocalDateTime agora);
}