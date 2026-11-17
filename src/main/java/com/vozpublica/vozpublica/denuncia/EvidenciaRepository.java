package com.vozpublica.vozpublica.denuncia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EvidenciaRepository extends JpaRepository<Evidencia, Long> {

    List<Evidencia> findByHistoricoIdInOrderByIdAsc(Collection<Long> historicoIds);

    /** 1 se a evidência pertence a uma publicação que não está oculta; 0 caso contrário. */
    @Query("SELECT COUNT(e) FROM Evidencia e, HistoricoStatus h "
            + "WHERE e.id = :id AND h.id = e.historicoId AND h.denuncia.oculta = false")
    long contarVisiveis(@Param("id") Long id);
}