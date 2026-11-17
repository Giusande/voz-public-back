package com.vozpublica.vozpublica.encaminhamento;

import com.vozpublica.vozpublica.denuncia.Denuncia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface AtrasoRepository extends Repository<Denuncia, Long> {

    String ORIGEM = " FROM denuncias d " + PrazoSql.JUNCOES
            + "WHERE d.status NOT IN ('RESOLVIDA', 'INDEFERIDA') AND d.oculta = FALSE "
            + "AND d.duplicada_de_id IS NULL AND " + PrazoSql.PRAZO + " < :hoje";

    @Query(value = "SELECT d.id AS id, d.titulo AS titulo, d.bairro AS bairro, c.nome AS categoria, "
            + "d.status AS status, TO_CHAR(" + PrazoSql.PRAZO + ", 'YYYY-MM-DD') AS prazo, u.nome AS unidade"
            + ORIGEM + " ORDER BY " + PrazoSql.PRAZO + ", d.id",
            countQuery = "SELECT COUNT(*)" + ORIGEM,
            nativeQuery = true)
    Page<DenunciaAtrasada> buscarAtrasadas(@Param("hoje") LocalDate hoje, Pageable pageable);
}