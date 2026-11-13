package com.vozpublica.vozpublica.denuncia;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public class FiltroDenuncia {

    public enum Ordem {
        RECENTES("Mais recentes"),
        INCENTIVADAS("Mais incentivadas"),
        URGENTES("Mais urgentes");

        private final String rotulo;
        Ordem(String rotulo) { this.rotulo = rotulo; }
        public String getRotulo() { return rotulo; }
    }

    private String bairro;
    private String regiao;
    private Long categoriaId;
    private Prioridade prioridade;
    private StatusDenuncia status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate de;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate ate;

    private Ordem ordem = Ordem.RECENTES;

    /** Monta a query string (já codificada) para os links de paginação, mantendo os filtros. */
    public String queryString(int pagina) {
        StringBuilder sb = new StringBuilder("pagina=").append(Math.max(pagina, 0));
        adicionar(sb, "bairro", bairro == null || bairro.isBlank() ? null : bairro.trim());
        adicionar(sb, "regiao", regiao == null || regiao.isBlank() ? null : regiao);
        adicionar(sb, "categoriaId", categoriaId);
        adicionar(sb, "prioridade", prioridade);
        adicionar(sb, "status", status);
        adicionar(sb, "de", de);
        adicionar(sb, "ate", ate);
        adicionar(sb, "ordem", ordem);
        return sb.toString();
    }

    private static void adicionar(StringBuilder sb, String nome, Object valor) {
        if (valor != null) {
            sb.append('&').append(nome).append('=')
              .append(UriUtils.encodeQueryParam(valor.toString(), StandardCharsets.UTF_8));
        }
    }

    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }
    public String getRegiao() { return regiao; }
    public void setRegiao(String regiao) { this.regiao = regiao; }
    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
    public Prioridade getPrioridade() { return prioridade; }
    public void setPrioridade(Prioridade prioridade) { this.prioridade = prioridade; }
    public StatusDenuncia getStatus() { return status; }
    public void setStatus(StatusDenuncia status) { this.status = status; }
    public LocalDate getDe() { return de; }
    public void setDe(LocalDate de) { this.de = de; }
    public LocalDate getAte() { return ate; }
    public void setAte(LocalDate ate) { this.ate = ate; }
    public Ordem getOrdem() { return ordem; }
    public void setOrdem(Ordem ordem) { this.ordem = ordem == null ? Ordem.RECENTES : ordem; }
}   