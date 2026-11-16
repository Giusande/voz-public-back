package com.vozpublica.vozpublica.relatorio;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CsvBuilderTest {

    @Test
    void neutralizaTextoQuePoderiaVirarFormula() {
        assertThat(CsvBuilder.celula("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvBuilder.celula("+55 81")).isEqualTo("'+55 81");
        assertThat(CsvBuilder.celula("-2")).isEqualTo("'-2");
        assertThat(CsvBuilder.celula("@SOMA(A1)")).isEqualTo("'@SOMA(A1)");
        assertThat(CsvBuilder.celula("Rua da Aurora")).isEqualTo("Rua da Aurora");
    }

    @Test
    void aplicaAspasQuandoHaSeparadorAspasOuQuebraDeLinha() {
        assertThat(CsvBuilder.celula("a;b")).isEqualTo("\"a;b\"");
        assertThat(CsvBuilder.celula("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(CsvBuilder.celula("linha1\nlinha2")).isEqualTo("\"linha1\nlinha2\"");
        assertThat(CsvBuilder.celula(null)).isEmpty();
    }

    @Test
    void geraBomSeparadorPontoEVirgulaELinhasCrlf() {
        String texto = new String(new CsvBuilder().linha("A", "B").linha("1", "2").bytes(),
                StandardCharsets.UTF_8);

        assertThat(texto).isEqualTo("\uFEFFA;B\r\n1;2\r\n");
    }
}