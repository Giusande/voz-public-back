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
class SidebarFixaTest {

    @Autowired private WebApplicationContext context;

    private String html(String url, String perfil) throws Exception {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                .perform(get(url).with(user("fixa." + perfil + "@teste.com").roles(perfil)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void paginasDeGestaoTemSidebarFixa() throws Exception {
        for (String url : new String[]{"/gestao/dashboard", "/gestao/atrasadas",
                "/gestao/reavaliacao", "/gestao/relatorios"}) {
            assertThat(html(url, "SERVIDOR")).as(url).contains("vp-sidebar--fixa").contains("class=\"gp\"");
        }
    }

    @Test
    void paginasAdminTemSidebarFixaComOsDoisGrupos() throws Exception {
        for (String url : new String[]{"/admin/moderacao", "/admin/usuarios", "/admin/auditoria",
                "/admin/unidades", "/admin/categorias"}) {
            String h = html(url, "ADMINISTRADOR");
            assertThat(h).as(url).contains("vp-sidebar--fixa").contains("vp-grupo--admin")
                    .contains("class=\"gp\"").contains("gp-cab");
        }
    }

    @Test
    void paginasComunsContinuamComGavetaSemSidebarFixa() throws Exception {
        assertThat(html("/mapa", "SERVIDOR")).contains("id=\"menu-lateral\"").doesNotContain("vp-sidebar--fixa");
    }

    @Test
    void usuariosContinuaMostrandoOsControlesDeConta() throws Exception {
        String h = html("/admin/usuarios", "ADMINISTRADOR");

        assertThat(h).contains("name=\"busca\"").contains("gp-tabela");
    }
}