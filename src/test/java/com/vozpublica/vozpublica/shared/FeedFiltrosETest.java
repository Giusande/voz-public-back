package com.vozpublica.vozpublica.shared;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.denuncia.Prioridade;
import com.vozpublica.vozpublica.moderacao.SinalizacaoRepository;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class FeedFiltrosETest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private SinalizacaoRepository sinalizacaoRepository;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User usuario(Role papel) {
        return userRepository.saveAndFlush(new User("Teste", "ff." + UUID.randomUUID() + "@teste.com",
                "hash", papel));
    }

    private Denuncia denuncia(User autor, String bairro) {
        return denunciaRepository.saveAndFlush(new Denuncia(autor,
                categoriaRepository.findByAtivaTrueOrderByNome().get(0), "Ocorrência " + UUID.randomUUID(),
                "Descrição de teste do problema.", null, bairro, "RPA 1 - Centro", null, null, Prioridade.MEDIA));
    }

    /** perfil == null: visitante. */
    private String html(String url, String perfil, String... params) throws Exception {
        var req = get(url);
        if (perfil != null) req.with(user("ff." + perfil + "@teste.com").roles(perfil));
        for (int i = 0; i < params.length; i += 2) req.param(params[i], params[i + 1]);
        return mvc().perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void indexTemOsFiltrosQueEramDaListagem() throws Exception {
        String h = html("/", null);

        assertThat(h).contains("name=\"bairro\"").contains("name=\"regiao\"").contains("name=\"categoriaId\"")
                .contains("name=\"prioridade\"").contains("name=\"status\"").contains("name=\"de\"")
                .contains("name=\"ate\"");
        assertThat(h).doesNotContain("Filtros avançados").doesNotContain("Mais comentadas");
    }

    @Test
    void denunciasEUmApelidoDoFeed() throws Exception {
        mvc().perform(get("/denuncias")).andExpect(status().isOk()).andExpect(view().name("index"))
                .andExpect(model().attributeExists("page", "contadores", "categorias", "ordens"));
    }

    @Test
    void filtrarPorBairroFuncionaNosDoisEnderecos() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        Denuncia d = denuncia(usuario(Role.CIDADAO), bairro);

        assertThat(html("/", null, "bairro", bairro)).contains(d.getTitulo());
        assertThat(html("/denuncias", null, "bairro", bairro)).contains(d.getTitulo());
        assertThat(html("/", null, "bairro", "inexistente-" + UUID.randomUUID())).doesNotContain(d.getTitulo());
    }

    @Test
    void botoesDeOrdemMantemOsFiltrosAtivos() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        denuncia(usuario(Role.CIDADAO), bairro);

        String h = html("/", null, "bairro", bairro);

        assertThat(h).contains("ordem=INCENTIVADAS").contains("ordem=URGENTES").contains("ordem=RECENTES");
        assertThat(h).contains("bairro=" + bairro);
    }

    @Test
    void formularioPreservaAOrdemEscolhida() throws Exception {
        String h = html("/", null, "ordem", "URGENTES");

        assertThat(h).containsPattern("<input[^>]*name=\"ordem\"[^>]*value=\"URGENTES\""
                + "|<input[^>]*value=\"URGENTES\"[^>]*name=\"ordem\"");
    }

    @Test
    void naoHaMaisEmojisNoFeedEExistemIcones() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        denuncia(usuario(Role.CIDADAO), bairro);

        String h = html("/", "CIDADAO", "bairro", bairro);

        assertThat(h).doesNotContain("♥").doesNotContain("♡").doesNotContain("📣")
                .doesNotContain("💬").doesNotContain("📍");
        assertThat(h).contains("bi-heart").contains("bi-megaphone").contains("bi-chat-dots-fill");
    }

    @Test
    void sidebarDoServidorTemSoOGrupoDaEquipeComContadorDeAtrasadas() throws Exception {
        Denuncia d = denuncia(usuario(Role.CIDADAO), "B-" + UUID.randomUUID());
        jdbc.update("UPDATE denuncias SET data_criacao = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2020, 1, 1, 12, 0)), d.getId());

        String h = html("/", "SERVIDOR");

        assertThat(h).contains("vp-grupo--equipe").contains("Gestão da equipe").contains("Servidor")
                .contains("id=\"nav-atrasadas\"");
        assertThat(h).doesNotContain("vp-grupo--admin").doesNotContain("/admin/usuarios");
    }

    @Test
    void sidebarDoAdministradorTemOsDoisGruposEOContadorDeModeracao() throws Exception {
        User autor = usuario(Role.CIDADAO);
        Denuncia d = denuncia(autor, "B-" + UUID.randomUUID());
        sinalizacaoRepository.inserir("DENUNCIA", d.getId(), d.getId(), autor.getId(), "SPAM", "");

        String h = html("/", "ADMINISTRADOR");

        assertThat(h).contains("vp-grupo--equipe").contains("vp-grupo--admin").contains("Administrador")
                .contains("Moderação e contas").contains("Configuração")
                .contains("/admin/moderacao").contains("/admin/auditoria").contains("/admin/unidades")
                .contains("id=\"nav-moderacao\"");
    }

    @Test
    void cidadaoEVisitanteNaoVeemOsGruposDeGestao() throws Exception {
        for (String perfil : new String[]{null, "CIDADAO"}) {
            String h = html("/", perfil);
            assertThat(h).doesNotContain("vp-grupo--equipe").doesNotContain("vp-grupo--admin")
                    .doesNotContain("/gestao/").doesNotContain("/admin/");
        }
    }

    @Test
    void paginasDeGestaoTambemRecebemASidebarNova() throws Exception {
        String h = html("/gestao/atrasadas", "SERVIDOR");

        assertThat(h).contains("vp-grupo--equipe").contains("bi-alarm-fill");
    }
}