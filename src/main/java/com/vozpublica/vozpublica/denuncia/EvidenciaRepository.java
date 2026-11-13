package com.vozpublica.vozpublica.denuncia;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface EvidenciaRepository extends JpaRepository<Evidencia, Long> {
    List<Evidencia> findByHistoricoIdInOrderByIdAsc(Collection<Long> historicoIds);
}