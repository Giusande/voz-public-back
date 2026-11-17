package com.vozpublica.vozpublica.user;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.denuncia.Prioridade;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
class PerfilTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User usuario(String nome) {
        return userRepository.saveAndFlush(new User(nome + " " + UUID.randomUUID(),
                "perfil." + UUID.randomUUID() + "@teste.com", "hash", Role.CIDADAO));
    }

    private Denuncia denuncia(User autor, String titulo, String bairro) {
        return denunciaRepository.saveAndFlush(new Denuncia(autor,
                categoriaRepository.findByAtivaTrueOrderByNome().get(0), titulo,
                "Descrição de teste do problema.", null, bairro, "RPA 1 - Centro",
                null, null, Prioridade.MEDIA));
    }

    private void limpar() {
        em.flush();
        em.clear();
    }

    /** quem == null: visitante. */
    private String html(String url, User quem) throws Exception {
        var req = get(url);
        if (quem != null) req.with(user(quem.getEmail()).roles("CIDADAO"));
        return mvc().perform(req).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions configurar(User quem, String... pares) throws Exception {
        var req = post("/perfil/configuracoes").with(user(quem.getEmail()).roles("CIDADAO")).with(csrf());
        for (int i = 0; i < pares.length; i += 2) req.param(pares[i], pares[i + 1]);
        return mvc().perform(req);
    }

    @Test
    void perfilPublicoAbreParaVisitanteSemExporEmail() throws Exception {
        User u = usuario("Maria");

        String pagina = html("/perfil/" + u.getId(), null);

        assertThat(pagina).contains(u.getName());
        assertThat(pagina).doesNotContain(u.getEmail());
    }

    @Test
    void perfilInexistenteDa404() throws Exception {
        mvc().perform(get("/perfil/999999999")).andExpect(status().isNotFound());
    }

    @Test
    void nomePublicoSubstituiONomeCompletoNoFeedENoDetalhe() throws Exception {
        User u = usuario("Nome Real");
        String bairro = "B-" + UUID.randomUUID();
        Denuncia d = denuncia(u, "Ocorrência pública", bairro);

        configurar(u, "nomePublico", "Maria S.", "perfilPublico", "true")
                .andExpect(flash().attributeExists("mensagem"));
        limpar();

        String feed = mvc().perform(get("/denuncias").param("bairro", bairro))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(feed).contains("Maria S.").doesNotContain(u.getName());

        String detalhe = html("/denuncias/" + d.getId(), null);
        assertThat(detalhe).contains("Maria S.").doesNotContain(u.getName());
    }

    @Test
    void perfilPrivadoSoAbreParaODono() throws Exception {
        User u = usuario("Reservada");
        User outro = usuario("Outro");
        configurar(u, "perfilPublico", "false");
        limpar();

        mvc().perform(get("/perfil/{id}", u.getId())).andExpect(status().isNotFound());
        mvc().perform(get("/perfil/{id}", u.getId()).with(user(outro.getEmail()).roles("CIDADAO")))
                .andExpect(status().isNotFound());
        assertThat(html("/perfil/" + u.getId(), u)).contains("perfil está");
    }

    @Test
    void contaSuspensaNaoTemPerfil() throws Exception {
        User u = usuario("Suspensa");
        jdbc.update("UPDATE usuarios SET ativo = FALSE WHERE id = ?", u.getId());
        limpar();

        mvc().perform(get("/perfil/{id}", u.getId())).andExpect(status().isNotFound());
    }

    @Test
    void perfilNaoListaOcultasNemDuplicadas() throws Exception {
        User u = usuario("Autora");
        String bairro = "B-" + UUID.randomUUID();
        Denuncia visivel = denuncia(u, "Visivel " + UUID.randomUUID(), bairro);
        Denuncia oculta = denuncia(u, "Oculta " + UUID.randomUUID(), bairro);
        Denuncia duplicada = denuncia(u, "Duplicada " + UUID.randomUUID(), bairro);
        jdbc.update("UPDATE denuncias SET oculta = TRUE WHERE id = ?", oculta.getId());
        jdbc.update("UPDATE denuncias SET duplicada_de_id = ? WHERE id = ?", visivel.getId(), duplicada.getId());
        limpar();

        String pagina = html("/perfil/" + u.getId(), null);

        assertThat(pagina).contains(visivel.getTitulo())
                .doesNotContain(oculta.getTitulo()).doesNotContain(duplicada.getTitulo());
    }

    @Test
    void dataEApoiosSoAparecemQuandoOUsuarioPermiteEOcultasNaoContam() throws Exception {
        User u = usuario("Apoiadora");
        User autor = usuario("Autor");
        Denuncia a = denuncia(autor, "A " + UUID.randomUUID(), "B-" + UUID.randomUUID());
        Denuncia oculta = denuncia(autor, "B " + UUID.randomUUID(), "B-" + UUID.randomUUID());
        jdbc.update("INSERT INTO incentivos (denuncia_id, usuario_id) VALUES (?, ?)", a.getId(), u.getId());
        jdbc.update("INSERT INTO incentivos (denuncia_id, usuario_id) VALUES (?, ?)", oculta.getId(), u.getId());
        jdbc.update("UPDATE denuncias SET oculta = TRUE WHERE id = ?", oculta.getId());
        limpar();

        String antes = html("/perfil/" + u.getId(), null);
        assertThat(antes).doesNotContain("Membro desde").doesNotContain("apoiada");

        configurar(u, "perfilPublico", "true", "exibirDataCadastro", "true", "exibirApoios", "true");
        limpar();

        String depois = html("/perfil/" + u.getId(), null);
        assertThat(depois).contains("Membro desde").contains("1 ocorrência(s) apoiada(s)");
    }

    @Test
    void configuracoesExigemLogin() throws Exception {
        mvc().perform(get("/perfil/configuracoes"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc().perform(post("/perfil/configuracoes").with(csrf()).param("nomePublico", "X"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc().perform(get("/perfil")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void nomeInvalidoEhRejeitado() throws Exception {
        User u = usuario("Valida");

        configurar(u, "nomePublico", "a\nb")
                .andExpect(status().isOk()).andExpect(view().name("perfil-configuracoes"))
                .andExpect(model().attributeHasFieldErrors("configForm", "nomePublico"));
        configurar(u, "nomePublico", "x".repeat(61))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("configForm", "nomePublico"));

        limpar();
        assertThat(userRepository.findById(u.getId()).orElseThrow().getNomePublico()).isNull();
    }

    @Test
    void nomeEmBrancoVoltaAoNomeCompleto() throws Exception {
        User u = usuario("Completa");
        configurar(u, "nomePublico", "Apelido", "perfilPublico", "true");
        limpar();
        assertThat(userRepository.findById(u.getId()).orElseThrow().getNomeExibicao()).isEqualTo("Apelido");

        configurar(u, "nomePublico", "   ", "perfilPublico", "true");
        limpar();
        User relido = userRepository.findById(u.getId()).orElseThrow();
        assertThat(relido.getNomePublico()).isNull();
        assertThat(relido.getNomeExibicao()).isEqualTo(u.getName());
    }

    @Test
    void htmlNoNomePublicoEhExibidoEscapado() throws Exception {
        User u = usuario("Xss");
        configurar(u, "nomePublico", "<b>x</b>", "perfilPublico", "true");
        limpar();

        String pagina = html("/perfil/" + u.getId(), null);

        assertThat(pagina).contains("&lt;b&gt;x&lt;/b&gt;").doesNotContain("<b>x</b>");
    }

    @Test
    void perfilRedirecionaParaOProprioPerfil() throws Exception {
        User u = usuario("Propria");

        mvc().perform(get("/perfil").with(user(u.getEmail()).roles("CIDADAO")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/perfil/" + u.getId()));
    }
}