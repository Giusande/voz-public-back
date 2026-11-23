package com.vozpublica.vozpublica.denuncia;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

public interface DenunciaPerfilRepository extends Repository<Denuncia, Long> {

    @EntityGraph(attributePaths = {"categoria", "autor"})
    Page<Denuncia> findByAutorIdAndOcultaFalseAndDuplicadaDeIdIsNullOrderByDataCriacaoDesc(
            Long autorId, Pageable pageable);

    @EntityGraph(attributePaths = {"categoria"})
    Page<Denuncia> findByAutorIdOrderByDataCriacaoDesc(Long autorId, Pageable pageable);
}