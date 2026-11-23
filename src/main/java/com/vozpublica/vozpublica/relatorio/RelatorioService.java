package com.vozpublica.vozpublica.relatorio;

import com.vozpublica.vozpublica.auditoria.AcaoAuditoria;
import com.vozpublica.vozpublica.auditoria.AuditoriaService;
import com.vozpublica.vozpublica.encaminhamento.PrazoSql;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
public class RelatorioService {

    public static final int MAX_LINHAS_CSV = 50_000;
    private static final int MAX_LINHAS_AGRUPADO = 1000;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public record Indicadores(long total, long pendentes, long emAnalise, long encaminhadas,
                              long emExecucao, long resolvidas, long indeferidas, long atrasadas,
                              Double tempoMedioDias) {

        public double percentualResolucao() {
            return total == 0 ? 0 : resolvidas * 100.0 / total;
        }

        public String percentualTexto() {
            return String.format(PT_BR, "%.1f", percentualResolucao());
        }

        public String tempoMedioTexto() {
            return tempoMedioDias == null ? "—" : String.format(PT_BR, "%.1f", tempoMedioDias);
        }
    }

    public record Linha(String grupo, Indicadores indicadores) {
        public int participacao(long totalGeral) {
            return totalGeral == 0 ? 0 : (int) Math.min(100, indicadores.total() * 100 / totalGeral);
        }
    }

    public record Painel(Indicadores geral, List<Linha> bairros, List<Linha> categorias, List<Linha> meses) {}

    @Transactional(readOnly = true)
    public Painel painelPublico(FiltroRelatorio filtro) {
        return new Painel(
                indicadores(filtro),
                agrupar(Dimensao.BAIRRO, filtro, 15),
                agrupar(Dimensao.CATEGORIA, filtro, 10),
                agrupar(Dimensao.MES, filtro, 12));
    }

    private record Consulta(String where, MapSqlParameterSource params) {}

    private static final String PRAZO = PrazoSql.PRAZO;

    private static final String ORIGEM = " FROM denuncias d " + PrazoSql.JUNCOES
            + "LEFT JOIN unidades u2 ON u2.id = u.pai_id "
            + "LEFT JOIN LATERAL (SELECT MAX(h.data_alteracao) AS data FROM historico_status h "
            + "WHERE h.denuncia_id = d.id AND h.status_novo = 'RESOLVIDA') r ON TRUE "
            + "WHERE d.oculta = FALSE AND d.duplicada_de_id IS NULL";

    private static final String METRICAS =
            "COUNT(*) AS total, "
            + "COUNT(*) FILTER (WHERE d.status IN ('RECEBIDA', 'REABERTA')) AS pendentes, "
            + "COUNT(*) FILTER (WHERE d.status = 'EM_ANALISE') AS em_analise, "
            + "COUNT(*) FILTER (WHERE d.status = 'ENCAMINHADA') AS encaminhadas, "
            + "COUNT(*) FILTER (WHERE d.status = 'EM_EXECUCAO') AS em_execucao, "
            + "COUNT(*) FILTER (WHERE d.status = 'RESOLVIDA') AS resolvidas, "
            + "COUNT(*) FILTER (WHERE d.status = 'INDEFERIDA') AS indeferidas, "
            + "COUNT(*) FILTER (WHERE d.status NOT IN ('RESOLVIDA', 'INDEFERIDA') AND " + PRAZO
            + " < :hoje) AS atrasadas, "
            + "AVG(EXTRACT(EPOCH FROM (r.data - d.data_criacao)) / 86400.0) "
            + "FILTER (WHERE d.status = 'RESOLVIDA' AND r.data IS NOT NULL) AS tempo_medio";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public RelatorioService(JdbcTemplate jdbcTemplate, AuditoriaService auditoria) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.auditoria = auditoria;
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public Painel painel(FiltroRelatorio filtro) {
        return new Painel(
                indicadores(filtro),
                agrupar(Dimensao.BAIRRO, filtro, 10),
                agrupar(Dimensao.CATEGORIA, filtro, 10),
                agrupar(Dimensao.MES, filtro, 12));
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public List<Linha> relatorioAgrupado(FiltroRelatorio filtro) {
        return agrupar(filtro.getDimensao(), filtro, MAX_LINHAS_AGRUPADO);
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional
    public byte[] exportarAgrupado(FiltroRelatorio filtro, String emailResponsavel) {
        List<Linha> linhas = agrupar(filtro.getDimensao(), filtro, MAX_LINHAS_AGRUPADO);

        CsvBuilder csv = new CsvBuilder().linha(filtro.getDimensao().getRotulo(), "Total", "Pendentes",
                "Em análise", "Encaminhadas", "Em execução", "Resolvidas", "Indeferidas", "Atrasadas",
                "% de resolução", "Tempo médio de atendimento (dias)");
        for (Linha l : linhas) {
            Indicadores i = l.indicadores();
            csv.linha(l.grupo(), String.valueOf(i.total()), String.valueOf(i.pendentes()),
                    String.valueOf(i.emAnalise()), String.valueOf(i.encaminhadas()),
                    String.valueOf(i.emExecucao()), String.valueOf(i.resolvidas()),
                    String.valueOf(i.indeferidas()), String.valueOf(i.atrasadas()),
                    i.percentualTexto(), i.tempoMedioDias() == null ? "" : i.tempoMedioTexto());
        }

        auditoria.registrar(emailResponsavel, AcaoAuditoria.RELATORIO_EXPORTADO, "RELATORIO", null, null,
                "Agrupado por " + filtro.getDimensao().getRotulo() + " (" + linhas.size()
                        + " linhas). Filtros: " + filtro.resumo());
        return csv.bytes();
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional
    public byte[] exportarOcorrencias(FiltroRelatorio filtro, String emailResponsavel) {
        Consulta c = consulta(filtro);
        String sql = "SELECT d.id, d.titulo, c.nome AS categoria, d.bairro, d.regiao, d.status, "
                + "d.prioridade_sugerida, d.prioridade_oficial, d.data_criacao, u.nome AS unidade, "
                + PRAZO + " AS prazo, "
                + "(d.status NOT IN ('RESOLVIDA', 'INDEFERIDA') AND " + PRAZO + " < :hoje) AS atrasada, "
                + "r.data AS data_resolucao"
                + ORIGEM + c.where() + " ORDER BY d.id LIMIT " + (MAX_LINHAS_CSV + 1);

        CsvBuilder csv = new CsvBuilder().linha("ID", "Título", "Categoria", "Bairro", "Região", "Situação",
                "Prioridade sugerida", "Prioridade oficial", "Registrada em", "Órgão responsável",
                "Prazo de referência", "Atrasada", "Resolvida em", "Dias até a resolução");
        int[] contador = {0};

        jdbc.query(sql, c.params(), (RowCallbackHandler) rs -> {
            if (++contador[0] > MAX_LINHAS_CSV) {
                throw new RelatorioException("Há mais de " + MAX_LINHAS_CSV
                        + " ocorrências nesse filtro. Refine o período ou os demais filtros.");
            }
            Timestamp criada = rs.getTimestamp("data_criacao");
            Timestamp resolvida = rs.getTimestamp("data_resolucao");
            String dias = "";
            if (resolvida != null && "RESOLVIDA".equals(rs.getString("status"))) {
                double d = Duration.between(criada.toLocalDateTime(), resolvida.toLocalDateTime()).toMinutes()
                        / 1440.0;
                dias = String.format(PT_BR, "%.1f", d);
            }
            csv.linha(String.valueOf(rs.getLong("id")), rs.getString("titulo"), rs.getString("categoria"),
                    rs.getString("bairro"), rs.getString("regiao"), rs.getString("status"),
                    rs.getString("prioridade_sugerida"), rs.getString("prioridade_oficial"),
                    criada.toLocalDateTime().format(DATA_HORA), rs.getString("unidade"),
                    rs.getDate("prazo").toLocalDate().format(DATA),
                    rs.getBoolean("atrasada") ? "Sim" : "Não",
                    resolvida == null ? "" : resolvida.toLocalDateTime().format(DATA_HORA), dias);
        });

        auditoria.registrar(emailResponsavel, AcaoAuditoria.RELATORIO_EXPORTADO, "RELATORIO", null, null,
                "Ocorrências detalhadas (" + contador[0] + " linhas). Filtros: " + filtro.resumo());
        return csv.bytes();
    }

    private Indicadores indicadores(FiltroRelatorio filtro) {
        Consulta c = consulta(filtro);
        return jdbc.queryForObject("SELECT " + METRICAS + ORIGEM + c.where(), c.params(),
                (rs, n) -> mapear(rs));
    }

    private List<Linha> agrupar(Dimensao dimensao, FiltroRelatorio filtro, int limite) {
        Consulta c = consulta(filtro);
        String ordem = dimensao == Dimensao.MES ? "1 DESC" : "total DESC, 1";
        String sql = "SELECT " + dimensao.sql() + " AS grupo, " + METRICAS + ORIGEM + c.where()
                + " GROUP BY 1 ORDER BY " + ordem + " LIMIT " + limite;
        List<Linha> linhas = new ArrayList<>(
                jdbc.query(sql, c.params(), (rs, n) -> new Linha(rs.getString("grupo"), mapear(rs))));
        if (dimensao == Dimensao.MES) {
            Collections.reverse(linhas); 
        }
        return linhas;
    }

    private static Indicadores mapear(ResultSet rs) throws SQLException {
        Object tempo = rs.getObject("tempo_medio");
        return new Indicadores(rs.getLong("total"), rs.getLong("pendentes"), rs.getLong("em_analise"),
                rs.getLong("encaminhadas"), rs.getLong("em_execucao"), rs.getLong("resolvidas"),
                rs.getLong("indeferidas"), rs.getLong("atrasadas"),
                tempo == null ? null : rs.getDouble("tempo_medio"));
    }

    private Consulta consulta(FiltroRelatorio f) {
        StringBuilder where = new StringBuilder();
        MapSqlParameterSource p = new MapSqlParameterSource("hoje", Date.valueOf(LocalDate.now()));

        if (f.getDe() != null) {
            where.append(" AND d.data_criacao >= :de");
            p.addValue("de", Timestamp.valueOf(f.getDe().atStartOfDay()));
        }
        if (f.getAte() != null) {
            where.append(" AND d.data_criacao < :ate");
            p.addValue("ate", Timestamp.valueOf(f.getAte().plusDays(1).atStartOfDay()));
        }
        if (f.getBairro() != null && !f.getBairro().isBlank()) {
            String termo = f.getBairro().trim();
            if (termo.length() > 100) termo = termo.substring(0, 100);
            where.append(" AND LOWER(d.bairro) LIKE :bairro");
            p.addValue("bairro", "%" + escapar(termo.toLowerCase(Locale.ROOT)) + "%");
        }
        if (f.getRegiao() != null && !f.getRegiao().isBlank()) {
            where.append(" AND d.regiao = :regiao");
            p.addValue("regiao", f.getRegiao());
        }
        if (f.getCategoriaId() != null) {
            where.append(" AND d.categoria_id = :categoriaId");
            p.addValue("categoriaId", f.getCategoriaId());
        }
        if (f.getUnidadeId() != null) {
            where.append(" AND (u.id = :unidadeId OR u.pai_id = :unidadeId OR u2.pai_id = :unidadeId)");
            p.addValue("unidadeId", f.getUnidadeId());
        }
        return new Consulta(where.toString(), p);
    }

    private static String escapar(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}