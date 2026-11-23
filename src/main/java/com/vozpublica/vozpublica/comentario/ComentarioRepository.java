package com.vozpublica.vozpublica.comentario;

import com.vozpublica.vozpublica.shared.ContagemPorDenuncia;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComentarioRepository extends JpaRepository<Comentario, Long> {

    @EntityGraph(attributePaths = "autor")
    List<Comentario> findByDenunciaIdOrderByDataCriacaoAsc(Long denunciaId);

    /** Visão pública: sem os ocultos. */
    @EntityGraph(attributePaths = "autor")
    List<Comentario> findByDenunciaIdAndOcultoFalseOrderByDataCriacaoAsc(Long denunciaId);

    long countByDenunciaId(Long denunciaId);

    @Query("SELECT new com.vozpublica.vozpublica.shared.ContagemPorDenuncia(c.denunciaId, COUNT(c)) "
            + "FROM Comentario c WHERE c.denunciaId IN :ids AND c.oculto = false GROUP BY c.denunciaId")
    List<ContagemPorDenuncia> contarPorDenuncia(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Comentario c WHERE c.id = :id")
    Optional<Comentario> buscarParaAtualizar(@Param("id") Long id);
}