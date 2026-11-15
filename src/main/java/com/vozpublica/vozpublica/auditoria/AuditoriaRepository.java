package com.vozpublica.vozpublica.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditoriaRepository
        extends JpaRepository<RegistroAuditoria, Long>, JpaSpecificationExecutor<RegistroAuditoria> {

    @Override
    @EntityGraph(attributePaths = "responsavel")
    Page<RegistroAuditoria> findAll(Specification<RegistroAuditoria> spec, Pageable pageable);
}