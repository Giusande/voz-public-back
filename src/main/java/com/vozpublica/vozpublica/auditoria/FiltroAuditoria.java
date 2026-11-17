package com.vozpublica.vozpublica.auditoria;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public class FiltroAuditoria {

    private AcaoAuditoria acao;
    private String responsavel; // e-mail

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate de;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate ate;

    public String queryString(int pagina) {
        StringBuilder sb = new StringBuilder("pagina=").append(Math.max(pagina, 0));
        adicionar(sb, "acao", acao);
        adicionar(sb, "responsavel", responsavel == null || responsavel.isBlank() ? null : responsavel.trim());
        adicionar(sb, "de", de);
        adicionar(sb, "ate", ate);
        return sb.toString();
    }

    private static void adicionar(StringBuilder sb, String nome, Object valor) {
        if (valor != null) {
            sb.append('&').append(nome).append('=')
              .append(UriUtils.encodeQueryParam(valor.toString(), StandardCharsets.UTF_8));
        }
    }

    public AcaoAuditoria getAcao() { return acao; }
    public void setAcao(AcaoAuditoria acao) { this.acao = acao; }
    public String getResponsavel() { return responsavel; }
    public void setResponsavel(String responsavel) { this.responsavel = responsavel; }
    public LocalDate getDe() { return de; }
    public void setDe(LocalDate de) { this.de = de; }
    public LocalDate getAte() { return ate; }
    public void setAte(LocalDate ate) { this.ate = ate; }
}