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

    @Query("SELECT COUNT(DISTINCT i.usuarioId) FROM Incentivo i WHERE i.denunciaId = :id "
            + "OR i.denunciaId IN (SELECT f.id FROM Denuncia f WHERE f.duplicadaDeId = :id)")
    long contarConsolidado(@Param("id") Long id);

    @Query("SELECT COUNT(i) FROM Incentivo i WHERE i.usuarioId = :usuarioId AND (i.denunciaId = :id "
            + "OR i.denunciaId IN (SELECT f.id FROM Denuncia f WHERE f.duplicadaDeId = :id))")
    long contarDoUsuarioConsolidado(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Query("SELECT new com.vozpublica.vozpublica.shared.ContagemPorDenuncia(d.id, COUNT(DISTINCT i.usuarioId)) "
            + "FROM Denuncia d, Denuncia x, Incentivo i "
            + "WHERE d.id IN :ids AND (x.id = d.id OR x.duplicadaDeId = d.id) AND i.denunciaId = x.id "
            + "GROUP BY d.id")
    List<ContagemPorDenuncia> contarConsolidadoPorDenuncia(@Param("ids") Collection<Long> ids);

    @Query("SELECT DISTINCT d.id FROM Denuncia d, Incentivo i "
            + "WHERE d.id IN :ids AND i.usuarioId = :usuarioId "
            + "AND (i.denunciaId = d.id OR i.denunciaId IN "
            + "(SELECT f.id FROM Denuncia f WHERE f.duplicadaDeId = d.id))")
    List<Long> idsIncentivadosConsolidado(@Param("usuarioId") Long usuarioId, @Param("ids") Collection<Long> ids);
}