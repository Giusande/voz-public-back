package com.vozpublica.vozpublica.relatorio;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class FiltroRelatorio {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate de;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate ate;

    private String bairro;
    private String regiao;
    private Long categoriaId;
    private Long unidadeId;
    private Dimensao dimensao = Dimensao.BAIRRO;

    public String queryString() {
        StringBuilder sb = new StringBuilder();
        adicionar(sb, "de", de);
        adicionar(sb, "ate", ate);
        adicionar(sb, "bairro", vazio(bairro) ? null : bairro.trim());
        adicionar(sb, "regiao", vazio(regiao) ? null : regiao);
        adicionar(sb, "categoriaId", categoriaId);
        adicionar(sb, "unidadeId", unidadeId);
        adicionar(sb, "dimensao", dimensao);
        return sb.toString();
    }

    public String resumo() {
        List<String> partes = new ArrayList<>();
        if (de != null) partes.add("de " + de);
        if (ate != null) partes.add("até " + ate);
        if (!vazio(bairro)) partes.add("bairro contém \"" + bairro.trim() + "\"");
        if (!vazio(regiao)) partes.add("região " + regiao);
        if (categoriaId != null) partes.add("categoria #" + categoriaId);
        if (unidadeId != null) partes.add("unidade #" + unidadeId);
        return partes.isEmpty() ? "nenhum" : String.join("; ", partes);
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    private static void adicionar(StringBuilder sb, String nome, Object valor) {
        if (valor == null) return;
        if (sb.length() > 0) sb.append('&');
        sb.append(nome).append('=').append(UriUtils.encodeQueryParam(valor.toString(), StandardCharsets.UTF_8));
    }

    public LocalDate getDe() { return de; }
    public void setDe(LocalDate de) { this.de = de; }
    public LocalDate getAte() { return ate; }
    public void setAte(LocalDate ate) { this.ate = ate; }
    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }
    public String getRegiao() { return regiao; }
    public void setRegiao(String regiao) { this.regiao = regiao; }
    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
    public Long getUnidadeId() { return unidadeId; }
    public void setUnidadeId(Long unidadeId) { this.unidadeId = unidadeId; }
    public Dimensao getDimensao() { return dimensao; }
    public void setDimensao(Dimensao dimensao) { this.dimensao = dimensao == null ? Dimensao.BAIRRO : dimensao; }
}   