package com.vozpublica.vozpublica.interacao;

import com.vozpublica.vozpublica.shared.ContagemPorDenuncia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface IncentivoRepository extends JpaRepository<Incentivo, Long> {

    long countByDenunciaId(Long denunciaId);

    boolean existsByDenunciaIdAndUsuarioId(Long denunciaId, Long usuarioId);

    @Modifying
    @Query(value = "INSERT INTO incentivos (denuncia_id, usuario_id) VALUES (:denunciaId, :usuarioId) "
            + "ON CONFLICT (denuncia_id, usuario_id) DO NOTHING", nativeQuery = true)
    int inserir(@Param("denunciaId") Long denunciaId, @Param("usuarioId") Long usuarioId);

    @Query("SELECT new com.vozpublica.vozpublica.shared.ContagemPorDenuncia(i.denunciaId, COUNT(i)) "
            + "FROM Incentivo i WHERE i.denunciaId IN :ids GROUP BY i.denunciaId")
    List<ContagemPorDenuncia> contarPorDenuncia(@Param("ids") Collection<Long> ids);
}