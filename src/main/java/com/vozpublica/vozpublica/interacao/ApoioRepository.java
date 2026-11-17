package com.vozpublica.vozpublica.interacao;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ApoioRepository extends Repository<Incentivo, Long> {

    /** Ocorrências incentivadas pelo usuário, ignorando as ocultas. */
    @Query("SELECT COUNT(i) FROM Incentivo i, Denuncia d "
            + "WHERE i.usuarioId = :id AND d.id = i.denunciaId AND d.oculta = false")
    long contarVisiveis(@Param("id") Long usuarioId);
}