package com.vozpublica.vozpublica.encaminhamento;

/**
 * Única definição, em SQL, do prazo de referência. Exige o alias "d" (denuncias).
 * Cria os aliases c (categorias), e (encaminhamento atual), ro (última reabertura) e u (unidade atual).
 */
public final class PrazoSql {

    private PrazoSql() {}

    public static final String JUNCOES =
            "JOIN categorias c ON c.id = d.categoria_id "
            + "LEFT JOIN LATERAL (SELECT x.prazo_especifico, x.unidade_id, x.data_encaminhamento "
            + "FROM encaminhamentos x WHERE x.denuncia_id = d.id ORDER BY x.id DESC LIMIT 1) e ON TRUE "
            + "LEFT JOIN LATERAL (SELECT MAX(h.data_alteracao) AS data FROM historico_status h "
            + "WHERE h.denuncia_id = d.id AND h.status_novo = 'REABERTA') ro ON TRUE "
            + "LEFT JOIN unidades u ON u.id = e.unidade_id ";

    public static final String PRAZO =
            "(CASE WHEN e.prazo_especifico IS NOT NULL "
            + "AND (ro.data IS NULL OR e.data_encaminhamento > ro.data) "
            + "THEN e.prazo_especifico "
            + "ELSE CAST(COALESCE(ro.data, d.data_criacao) + c.prazo_referencia_dias * INTERVAL '1 day' AS date) END)";
}