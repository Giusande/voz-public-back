package com.vozpublica.vozpublica.confirmacao;

import com.vozpublica.vozpublica.denuncia.Denuncia;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReavaliacaoRepository extends Repository<Denuncia, Long> {

    String COLUNAS = "SELECT d.id AS id, d.titulo AS titulo, d.bairro AS bairro, "
            + "COUNT(*) FILTER (WHERE c.tipo = 'CONTINUA_PRESENTE') AS continua, "
            + "COUNT(*) FILTER (WHERE c.tipo = 'APARENTEMENTE_RESOLVIDO') AS resolvido";

    /** Só contam os relatos feitos DEPOIS da última mudança para RESOLVIDA. */
    String BASE = " FROM denuncias d "
            + "JOIN LATERAL (SELECT MAX(h.data_alteracao) AS data FROM historico_status h "
            + "WHERE h.denuncia_id = d.id AND h.status_novo = 'RESOLVIDA') r ON TRUE "
            + "JOIN confirmacoes c ON c.denuncia_id = d.id AND c.data_registro > r.data "
            + "WHERE d.status = 'RESOLVIDA' AND d.oculta = FALSE AND d.duplicada_de_id IS NULL";

    String AGRUPA = " GROUP BY d.id, d.titulo, d.bairro "
            + "HAVING COUNT(*) FILTER (WHERE c.tipo = 'CONTINUA_PRESENTE') >= :minimo "
            + "AND COUNT(*) FILTER (WHERE c.tipo = 'CONTINUA_PRESENTE') "
            + "> COUNT(*) FILTER (WHERE c.tipo = 'APARENTEMENTE_RESOLVIDO')";

    @Query(value = COLUNAS + BASE + AGRUPA + " ORDER BY continua DESC, d.id LIMIT 200", nativeQuery = true)
    List<DenunciaDivergente> listar(@Param("minimo") long minimo);

    @Query(value = COLUNAS + BASE + " AND d.id = :id" + AGRUPA, nativeQuery = true)
    List<DenunciaDivergente> deUma(@Param("id") Long id, @Param("minimo") long minimo);
}