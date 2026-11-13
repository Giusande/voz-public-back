package com.vozpublica.vozpublica.comentario;

import com.vozpublica.vozpublica.shared.ContagemPorDenuncia;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ComentarioRepository extends JpaRepository<Comentario, Long> {

    @EntityGraph(attributePaths = "autor")
    List<Comentario> findByDenunciaIdOrderByDataCriacaoAsc(Long denunciaId);

    long countByDenunciaId(Long denunciaId);

    @Query("SELECT new com.vozpublica.vozpublica.shared.ContagemPorDenuncia(c.denunciaId, COUNT(c)) "
            + "FROM Comentario c WHERE c.denunciaId IN :ids GROUP BY c.denunciaId")
    List<ContagemPorDenuncia> contarPorDenuncia(@Param("ids") Collection<Long> ids);
}