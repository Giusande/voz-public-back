package com.vozpublica.vozpublica.denuncia;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FotoRepository extends JpaRepository<Foto, Long> {
    List<Foto> findByDenunciaIdOrderByIdAsc(Long denunciaId);

    /** A foto pertence a uma publicação que não está oculta. */
    boolean existsByIdAndDenunciaOcultaFalse(Long id);
}