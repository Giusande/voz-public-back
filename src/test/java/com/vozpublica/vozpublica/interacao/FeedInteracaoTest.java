package com.vozpublica.vozpublica.interacao;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.denuncia.Prioridade;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class FeedInteracaoTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private CurtidaRepository curtidaRepository;
    @Autowired private IncentivoRepository incentivoRepository;
    @PersistenceContext private EntityManager em;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User usuario() {
        return userRepository.saveAndFlush(new User("Feed", "feed." + UUID.randomUUID() + "@teste.com",
                "hash", Role.CIDADAO));
    }

    private Denuncia denuncia(User autor, String bairro) {
        return denunciaRepository.saveAndFlush(new Denuncia(autor,
                categoriaRepository.findByAtivaTrueOrderByNome().get(0), "Ocorrência " + UUID.randomUUID(),
                "Descrição de teste do problema.", null, bairro, "RPA 1 - Centro", null, null, Prioridade.MEDIA));
    }

    private ResultActions acao(User u, Long id, String acao, String voltar) throws Exception {
        var b = post("/denuncias/" + id + "/" + acao).with(user(u.getEmail()).roles("CIDADAO")).with(csrf());
        if (voltar != null) b.param("voltar", voltar);
        return mvc().perform(b);
    }

    @Test
    void curtirVoltaAoFeedNaPosicaoDoCardEAlterna() throws Exception {
        User eu = usuario();
        Denuncia d = denuncia(usuario(), "B-" + UUID.randomUUID());

        acao(eu, d.getId(), "curtir", "/?pagina=0").andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?pagina=0#post-" + d.getId()));
        em.flush();
        assertThat(curtidaRepository.countByDenunciaId(d.getId())).isEqualTo(1);

        acao(eu, d.getId(), "curtir", "/?pagina=0");
        em.flush();
        assertThat(curtidaRepository.countByDenunciaId(d.getId())).isZero();
    }

    @Test
    void incentivarContaUmaVezEVoltaAoFeed() throws Exception {
        User eu = usuario();
        Denuncia d = denuncia(usuario(), "B-" + UUID.randomUUID());

        acao(eu, d.getId(), "incentivar", "/denuncias?pagina=1&bairro=Boa%20Vista")
                .andExpect(redirectedUrl("/denuncias?pagina=1&bairro=Boa%20Vista#post-" + d.getId()));
        acao(eu, d.getId(), "incentivar", "/?pagina=0");
        em.flush();

        assertThat(incentivoRepository.countByDenunciaId(d.getId())).isEqualTo(1);
    }

    @Test
    void retornoParaOutroLugarEIgnoradoENaoHaRedirecionamentoAberto() throws Exception {
        User eu = usuario();
        Denuncia d = denuncia(usuario(), "B-" + UUID.randomUUID());

        for (String ruim : new String[]{"//site-malicioso.com", "https://site-malicioso.com",
                "/denuncias/../admin", "/gestao/dashboard", "/?x=a b", "/denuncias\\evil"}) {
            acao(eu, d.getId(), "curtir", ruim).andExpect(redirectedUrl("/denuncias/" + d.getId()));
        }
    }

    @Test
    void semRetornoContinuaVoltandoAoDetalhe() throws Exception {
        User eu = usuario();
        Denuncia d = denuncia(usuario(), "B-" + UUID.randomUUID());

        acao(eu, d.getId(), "incentivar", null).andExpect(redirectedUrl("/denuncias/" + d.getId()));
    }

    @Test
    void homeMostraCurtidoEIncentivadoParaQuemJaFez() throws Exception {
        User eu = usuario();
        String bairro = "B-" + UUID.randomUUID();
        Denuncia d = denuncia(usuario(), bairro);
        acao(eu, d.getId(), "curtir", "/");
        acao(eu, d.getId(), "incentivar", "/");
        em.flush();
        em.clear();

        String html = mvc().perform(get("/").param("bairro", bairro).with(user(eu.getEmail()).roles("CIDADAO")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("♥ Curtido").contains("📣 Incentivado").contains("vp-btn--curtiu")
                .contains("id=\"post-" + d.getId() + "\"");
        assertThat(html).doesNotContain("♡ Curtir");
    }

    @Test
    void homeOferecePostParaQuemAindaNaoInteragiu() throws Exception {
        User eu = usuario();
        String bairro = "B-" + UUID.randomUUID();
        Denuncia d = denuncia(usuario(), bairro);

        String html = mvc().perform(get("/").param("bairro", bairro).with(user(eu.getEmail()).roles("CIDADAO")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("/denuncias/" + d.getId() + "/curtir")
                .contains("/denuncias/" + d.getId() + "/incentivar").contains("name=\"voltar\"")
                .contains("♡ Curtir").contains("📣 Incentivar");
    }

    @Test
    void visitanteVaiAoLoginEVoltaParaAOcorrenciaESemFormularios() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        denuncia(usuario(), bairro);

        String html = mvc().perform(get("/").param("bairro", bairro))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("/acesso?retorno=").doesNotContain("/curtir\"").doesNotContain("/incentivar\"");
    }

    @Test
    void listaDeFiltrosAvancadosTambemMostraOEstado() throws Exception {
        User eu = usuario();
        String bairro = "B-" + UUID.randomUUID();
        Denuncia d = denuncia(usuario(), bairro);
        acao(eu, d.getId(), "curtir", "/denuncias");
        em.flush();
        em.clear();

        String html = mvc().perform(get("/denuncias").param("bairro", bairro)
                        .with(user(eu.getEmail()).roles("CIDADAO")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("♥ Curtido");
    }

    @Test
    void menuLateralTemIconesEMantemOsTextos() throws Exception {
        String html = mvc().perform(get("/denuncias").with(user("menu@teste.com").roles("ADMINISTRADOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("bootstrap-icons").contains("bi-house-door-fill").contains("bi-geo-alt-fill")
                .contains("bi-shield-exclamation").contains(">Sair<").contains(">Feed<");
    }
}