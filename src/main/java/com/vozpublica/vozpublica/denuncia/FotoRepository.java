package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.shared.ContagemPorDenuncia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FotoRepository extends JpaRepository<Foto, Long> {

    List<Foto> findByDenunciaIdOrderByIdAsc(Long denunciaId);

    /** A foto pertence a uma publicação que não está oculta. */
    boolean existsByIdAndDenunciaOcultaFalse(Long id);

    /** Por ocorrência, o id da primeira foto (o "total" do registro é esse id). */
    @Query("SELECT new com.vozpublica.vozpublica.shared.ContagemPorDenuncia(f.denuncia.id, MIN(f.id)) "
            + "FROM Foto f WHERE f.denuncia.id IN :ids GROUP BY f.denuncia.id")
    List<ContagemPorDenuncia> primeirasFotos(@Param("ids") Collection<Long> ids);
}