package com.vozpublica.vozpublica.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class NavegacaoTest {

    @Autowired private WebApplicationContext context;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** perfil == null: visitante. */
    private String html(String url, String perfil) throws Exception {
        var req = get(url);
        if (perfil != null) req.with(user("nav." + perfil + "@teste.com").roles(perfil));
        return mvc().perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void topbarTemDuasLinhasParaTodosOsPerfis() throws Exception {
        for (String perfil : new String[]{null, "CIDADAO", "SERVIDOR", "ADMINISTRADOR"}) {
            String h = html("/", perfil);
            assertThat(h).contains("vp-topbar__in").contains("vp-topbar__nav")
                    .contains("+ Nova denúncia").contains("/mapa").contains("/transparencia")
                    .contains("prioridade=URGENTE").contains("status=EM_EXECUCAO").contains("status=RESOLVIDA");
        }
    }

    @Test
    void cidadaoEVisitanteNaoTemSidebarNemBotaoDeMenu() throws Exception {
        for (String perfil : new String[]{null, "CIDADAO"}) {
            for (String url : new String[]{"/", "/mapa", "/transparencia"}) {
                String h = html(url, perfil);
                assertThat(h).doesNotContain("id=\"menu-lateral\"").doesNotContain("href=\"#menu-lateral\"")
                        .doesNotContain("vp-grupo");
            }
        }
        assertThat(html("/", "CIDADAO")).contains("vp-grid--solo");
    }

    @Test
    void servidorTemSidebarComBotaoDeMenuMasSemAdministracao() throws Exception {
        String h = html("/", "SERVIDOR");

        assertThat(h).contains("id=\"menu-lateral\"").contains("href=\"#menu-lateral\"")
                .contains("vp-grupo--equipe").contains("Gestão da equipe");
        assertThat(h).doesNotContain("vp-grupo--admin").doesNotContain("/admin/usuarios");
    }

    @Test
    void administradorTemSidebarCompleta() throws Exception {
        String h = html("/", "ADMINISTRADOR");

        assertThat(h).contains("id=\"menu-lateral\"").contains("vp-grupo--equipe").contains("vp-grupo--admin")
                .contains("/admin/moderacao").contains("/admin/auditoria");
    }

    @Test
    void linksDeUsoComumFicamNaTopbarSoParaQuemEstaLogado() throws Exception {
        String visitante = html("/", null);
        assertThat(visitante).doesNotContain("/minhas-denuncias").doesNotContain("/notificacoes")
                .doesNotContain(">Sair<").contains(">Entrar<");

        String logado = html("/", "CIDADAO");
        assertThat(logado).contains("/minhas-denuncias").contains("/notificacoes").contains(">Sair<")
                .contains("Meu perfil").doesNotContain(">Entrar<");
    }

    @Test
    void sairContinuaDisponivelParaOCidadaoSemSidebar() throws Exception {
        String h = html("/mapa", "CIDADAO");

        assertThat(h).contains("/logout").contains(">Sair<").doesNotContain("id=\"menu-lateral\"");
    }

    @Test
    void paginasDeGestaoMostramAMesmaTopbarEASidebarDaEquipe() throws Exception {
        String h = html("/gestao/dashboard", "SERVIDOR");

        assertThat(h).contains("vp-topbar__nav").contains("id=\"menu-lateral\"").contains("bi-speedometer2");
    }

    @Test
    void itemAtualDaTopbarFicaDestacado() throws Exception {
        assertThat(html("/mapa", "CIDADAO")).containsPattern("vp-navlink ativo[^>]*>\\s*<i[^>]*bi-geo-alt-fill");
    }
}