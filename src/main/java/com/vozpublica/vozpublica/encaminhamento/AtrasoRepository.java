package com.vozpublica.vozpublica.encaminhamento;

import com.vozpublica.vozpublica.denuncia.Denuncia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface AtrasoRepository extends Repository<Denuncia, Long> {

    String PRAZO = "COALESCE(e.prazo_especifico, "
            + "CAST(d.data_criacao + c.prazo_referencia_dias * INTERVAL '1 day' AS date))";

    String ORIGEM = " FROM denuncias d "
            + "JOIN categorias c ON c.id = d.categoria_id "
            + "LEFT JOIN LATERAL (SELECT x.prazo_especifico, x.unidade_id FROM encaminhamentos x "
            + "WHERE x.denuncia_id = d.id ORDER BY x.id DESC LIMIT 1) e ON TRUE "
            + "LEFT JOIN unidades u ON u.id = e.unidade_id "
            + "WHERE d.status NOT IN ('RESOLVIDA', 'INDEFERIDA') AND " + PRAZO + " < :hoje";

    @Query(value = "SELECT d.id AS id, d.titulo AS titulo, d.bairro AS bairro, c.nome AS categoria, "
            + "d.status AS status, TO_CHAR(" + PRAZO + ", 'YYYY-MM-DD') AS prazo, u.nome AS unidade"
            + ORIGEM + " ORDER BY " + PRAZO + ", d.id",
            countQuery = "SELECT COUNT(*)" + ORIGEM,
            nativeQuery = true)
    Page<DenunciaAtrasada> buscarAtrasadas(@Param("hoje") LocalDate hoje, Pageable pageable);
}