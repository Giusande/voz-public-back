package com.vozpublica.vozpublica.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PaginasGestaoVisualTest {

    @Autowired private WebApplicationContext context;

    private String html(String url) throws Exception {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                .perform(get(url).with(user("vis.gestao@teste.com").roles("SERVIDOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void asQuatroPaginasUsamOLayoutNovoEMantemOMenu() throws Exception {
        for (String url : new String[]{"/gestao/dashboard", "/gestao/atrasadas",
                "/gestao/reavaliacao", "/gestao/relatorios"}) {
            String h = html(url);
            assertThat(h).as(url).contains("class=\"gp\"").contains("gp-cab").contains("vp-topbar__nav");
        }
    }

    @Test
    void dashboardTemOsIndicadoresEFiltros() throws Exception {
        String h = html("/gestao/dashboard");

        assertThat(h).contains("gp-kpis").contains("Distribuição por situação")
                .contains("Como cada indicador é calculado").contains("name=\"categoriaId\"");
    }

    @Test
    void relatoriosTemOsDoisCsvEOSeletorDeAgrupamento() throws Exception {
        String h = html("/gestao/relatorios");

        assertThat(h).contains("agrupado.csv").contains("ocorrencias.csv").contains("name=\"dimensao\"");
    }
}