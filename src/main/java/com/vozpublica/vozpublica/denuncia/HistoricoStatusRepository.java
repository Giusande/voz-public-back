package com.vozpublica.vozpublica.denuncia;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HistoricoStatusRepository extends JpaRepository<HistoricoStatus, Long> {
    List<HistoricoStatus> findByDenunciaIdOrderByDataAlteracaoAsc(Long denunciaId);
}