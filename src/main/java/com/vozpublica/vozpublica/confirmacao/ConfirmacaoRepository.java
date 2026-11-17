package com.vozpublica.vozpublica.confirmacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConfirmacaoRepository extends JpaRepository<Confirmacao, Long> {

    /** Um relato por pessoa e ocorrência: registrar de novo troca o tipo e renova a data. */
    @Modifying
    @Query(value = "INSERT INTO confirmacoes (denuncia_id, usuario_id, tipo) "
            + "VALUES (:denunciaId, :usuarioId, :tipo) "
            + "ON CONFLICT (denuncia_id, usuario_id) "
            + "DO UPDATE SET tipo = EXCLUDED.tipo, data_registro = CURRENT_TIMESTAMP", nativeQuery = true)
    int registrar(@Param("denunciaId") Long denunciaId, @Param("usuarioId") Long usuarioId,
                  @Param("tipo") String tipo);

    @Modifying
    @Query("DELETE FROM Confirmacao c WHERE c.denunciaId = :denunciaId AND c.usuarioId = :usuarioId")
    int remover(@Param("denunciaId") Long denunciaId, @Param("usuarioId") Long usuarioId);

    long countByDenunciaIdAndTipo(Long denunciaId, TipoConfirmacao tipo);

    Optional<Confirmacao> findByDenunciaIdAndUsuarioId(Long denunciaId, Long usuarioId);
}