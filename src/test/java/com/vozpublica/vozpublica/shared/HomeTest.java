package com.vozpublica.vozpublica.shared;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.denuncia.Prioridade;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class HomeTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private Denuncia denuncia(String titulo, String bairro) {
        User autor = userRepository.saveAndFlush(new User("Autora Home", "home." + UUID.randomUUID() + "@teste.com",
                "hash", Role.CIDADAO));
        return denunciaRepository.saveAndFlush(new Denuncia(autor,
                categoriaRepository.findByAtivaTrueOrderByNome().get(0), titulo,
                "Descrição de teste do problema.", null, bairro, "RPA 1 - Centro", null, null, Prioridade.ALTA));
    }

    private String html(String url, String... params) throws Exception {
        var req = get(url);
        for (int i = 0; i < params.length; i += 2) req.param(params[i], params[i + 1]);
        return mvc().perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void paginaInicialEPublicaETemAsTresAreas() throws Exception {
        mvc().perform(get("/")).andExpect(status().isOk()).andExpect(view().name("index"))
                .andExpect(model().attributeExists("page", "contadores", "fotos", "painel", "maisIncentivadas"));

        String h = html("/");
        assertThat(h).contains("Ocorrências do Recife").contains("Resumo da cidade")
                .contains("Mapa de ocorrências").contains("Mais incentivadas")
                .contains("Categorias mais frequentes").contains("+ Nova denúncia")
                .contains("Mais recentes").contains("Mais incentivadas").contains("Urgentes");
        assertThat(h).doesNotContain("Mais comentadas").doesNotContain("Pesquisar");
    }

    @Test
    void menuLateralTemOsAtalhosEOsFiltrosReais() throws Exception {
        String h = html("/");

        assertThat(h).contains("/mapa").contains("/transparencia")
                .contains("prioridade=URGENTE").contains("status=EM_EXECUCAO").contains("status=RESOLVIDA");
    }

    @Test
    void cardMostraOsDadosReaisEEscapaHtml() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        denuncia("<b>negrito</b> buraco", bairro);

        String h = html("/", "bairro", bairro);

        assertThat(h).contains("&lt;b&gt;negrito&lt;/b&gt;").doesNotContain("<b>negrito</b>")
                .contains("Ver ocorrência").contains("Alta").contains("Recebida");
    }

    @Test
    void filtroDeUrgentesEValoresInvalidosFuncionam() throws Exception {
        mvc().perform(get("/").param("prioridade", "URGENTE")).andExpect(status().isOk());
        mvc().perform(get("/").param("status", "XYZ").param("ordem", "lixo").param("pagina", "0"))
                .andExpect(status().isOk());
    }

    @Test
    void visitanteVeEntrarEPerfilSoParaLogado() throws Exception {
        String visitante = html("/");
        assertThat(visitante).contains(">Entrar<").doesNotContain("Meu perfil").doesNotContain(">Sair<");

        String logado = mvc().perform(get("/").with(user("home@teste.com").roles("CIDADAO")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(logado).contains("Meu perfil").contains("/minhas-denuncias").contains(">Sair<")
                .doesNotContain(">Entrar<");
    }

    @Test
    void painelNaoMostraDadosPessoais() throws Exception {
        String h = html("/");

        assertThat(h).doesNotContain("@teste.com");
    }
}