package com.vozpublica.vozpublica.relatorio;

import java.nio.charset.StandardCharsets;

final class CsvBuilder {

    private static final char SEPARADOR = ';';

    private final StringBuilder sb = new StringBuilder("\uFEFF");

    CsvBuilder linha(String... celulas) {
        for (int i = 0; i < celulas.length; i++) {
            if (i > 0) sb.append(SEPARADOR);
            sb.append(celula(celulas[i]));
        }
        sb.append("\r\n");
        return this;
    }

    static String celula(String valor) {
        if (valor == null || valor.isEmpty()) return "";
        String v = valor;
        char primeiro = v.charAt(0);
        if (primeiro == '=' || primeiro == '+' || primeiro == '-' || primeiro == '@'
                || primeiro == '\t' || primeiro == '\r') {
            v = "'" + v;
        }
        if (v.indexOf(SEPARADOR) >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    byte[] bytes() {
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}