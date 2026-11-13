package com.vozpublica.vozpublica.denuncia;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface DenunciaRepository
        extends JpaRepository<Denuncia, Long>, JpaSpecificationExecutor<Denuncia> {

    @EntityGraph(attributePaths = {"categoria", "autor"})
    Page<Denuncia> findAllByOrderByDataCriacaoDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"categoria", "autor"})
    Optional<Denuncia> findComDetalhesById(Long id);

    /** Listagem com filtros: carrega categoria e autor junto, evitando N+1. */
    @Override
    @EntityGraph(attributePaths = {"categoria", "autor"})
    Page<Denuncia> findAll(Specification<Denuncia> spec, Pageable pageable);

    @Query("""
            SELECT d FROM Denuncia d
            WHERE d.categoria.id = :categoriaId
              AND d.status NOT IN :encerradas
              AND (LOWER(d.bairro) = :bairro
                   OR (d.latitude BETWEEN :latMin AND :latMax
                       AND d.longitude BETWEEN :lonMin AND :lonMax))
            ORDER BY d.dataCriacao DESC
            """)
    List<Denuncia> buscarCandidatas(@Param("categoriaId") Long categoriaId,
                                    @Param("encerradas") List<StatusDenuncia> encerradas,
                                    @Param("bairro") String bairro,
                                    @Param("latMin") BigDecimal latMin,
                                    @Param("latMax") BigDecimal latMax,
                                    @Param("lonMin") BigDecimal lonMin,
                                    @Param("lonMax") BigDecimal lonMax,
                                    Pageable pageable);
}