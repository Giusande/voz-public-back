package com.vozpublica.vozpublica.relatorio;

/** Expressões SQL fixas (nunca vêm do usuário). */
public enum Dimensao {
    BAIRRO("Bairro", "d.bairro"),
    REGIAO("Região", "d.regiao"),
    CATEGORIA("Categoria", "c.nome"),
    ORGAO("Órgão responsável atual", "COALESCE(u.nome, 'Sem encaminhamento')"),
    STATUS("Situação", "d.status"),
    MES("Mês de registro", "TO_CHAR(d.data_criacao, 'YYYY-MM')");

    private final String rotulo;
    private final String sql;

    Dimensao(String rotulo, String sql) {
        this.rotulo = rotulo;
        this.sql = sql;
    }

    public String getRotulo() { return rotulo; }

    String sql() { return sql; }
}