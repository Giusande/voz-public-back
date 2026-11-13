package com.vozpublica.vozpublica.denuncia;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DenunciaGestaoRepository extends Repository<Denuncia, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Denuncia d WHERE d.id = :id")
    Optional<Denuncia> buscarParaAtualizar(@Param("id") Long id);
}