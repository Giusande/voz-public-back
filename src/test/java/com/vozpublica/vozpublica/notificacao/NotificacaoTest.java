package com.vozpublica.vozpublica.notificacao;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.denuncia.Prioridade;
import com.vozpublica.vozpublica.unidade.TipoUnidade;
import com.vozpublica.vozpublica.unidade.Unidade;
import com.vozpublica.vozpublica.unidade.UnidadeRepository;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class NotificacaoTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private NotificacaoRepository notificacaoRepository;
    @Autowired private NotificacaoService notificacaoService;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User usuario(String nome, Role papel) {
        return userRepository.saveAndFlush(new User(nome + " " + UUID.randomUUID(),
                "notif." + UUID.randomUUID() + "@teste.com", "hash", papel));
    }

    private Denuncia denuncia(User autor, String titulo) {
        return denunciaRepository.saveAndFlush(new Denuncia(autor,
                categoriaRepository.findByAtivaTrueOrderByNome().get(0), titulo,
                "Descrição de teste do problema.", null, "B-" + UUID.randomUUID(), "RPA 1 - Centro",
                null, null, Prioridade.MEDIA));
    }

    private void limpar() {
        em.flush();
        em.clear();
    }

    private List<Notificacao> de(User u) {
        limpar();
        return notificacaoRepository.findAll().stream().filter(n -> n.getUsuarioId().equals(u.getId())).toList();
    }

    private ResultActions postar(User quem, String caminho, String... pares) throws Exception {
        var b = post(caminho).with(user(quem.getEmail()).roles(quem.getRole().name())).with(csrf());
        for (int i = 0; i < pares.length; i += 2) b.param(pares[i], pares[i + 1]);
        return mvc().perform(b);
    }

    private ResultActions mudarStatus(User quem, Long id, String novo, String obs) throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart("/gestao/denuncias/{id}/status", id);
        b.with(user(quem.getEmail()).roles(quem.getRole().name())).with(csrf());
        b.param("novoStatus", novo);
        b.param("observacao", obs);
        return mvc().perform(b);
    }

    private String pagina(String url, User quem) throws Exception {
        return mvc().perform(get(url).with(user(quem.getEmail()).roles(quem.getRole().name())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void mudancaDeSituacaoNotificaOAutorMasNaoQuemFezAAcao() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor, "Buraco " + UUID.randomUUID());

        mudarStatus(serv, d.getId(), "EM_ANALISE", "Vamos ver").andExpect(flash().attributeExists("mensagem"));

        List<Notificacao> doAutor = de(autor);
        assertThat(doAutor).hasSize(1);
        assertThat(doAutor.get(0).getTipo()).isEqualTo(TipoNotificacao.SITUACAO);
        assertThat(doAutor.get(0).getMensagem()).contains("Em análise").contains(d.getTitulo());
        assertThat(doAutor.get(0).getDenunciaId()).isEqualTo(d.getId());
        assertThat(doAutor.get(0).isLida()).isFalse();
        assertThat(de(serv)).isEmpty();
    }

    @Test
    void acaoRejeitadaNaoGeraNotificacao() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor, "Qualquer " + UUID.randomUUID());

        mudarStatus(serv, d.getId(), "RESOLVIDA", "Pulando etapas").andExpect(flash().attributeExists("erro"));

        assertThat(de(autor)).isEmpty();
    }

    @Test
    void reaberturaTemTipoPropriodado() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor, "Reaberta " + UUID.randomUUID());
        jdbc.update("UPDATE denuncias SET status = 'RESOLVIDA' WHERE id = ?", d.getId());
        limpar();

        mudarStatus(serv, d.getId(), "REABERTA", "Voltou a ocorrer").andExpect(flash().attributeExists("mensagem"));

        assertThat(de(autor)).extracting(Notificacao::getTipo).containsExactly(TipoNotificacao.REABERTURA);
    }

    @Test
    void comentarioDeOutraPessoaNotificaOAutorMasOProprioNao() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User outro = usuario("Outro", Role.CIDADAO);
        Denuncia d = denuncia(autor, "Comentada " + UUID.randomUUID());
        String url = "/denuncias/" + d.getId() + "/comentarios";

        postar(autor, url, "conteudo", "Atualizando por aqui").andExpect(status().is3xxRedirection());
        assertThat(de(autor)).isEmpty();

        postar(outro, url, "conteudo", "Também vejo").andExpect(status().is3xxRedirection());
        List<Notificacao> lista = de(autor);
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).getTipo()).isEqualTo(TipoNotificacao.COMENTARIO);
        assertThat(de(outro)).isEmpty();
    }

    @Test
    void prioridadeOficialEEncaminhamentoNotificamSemRevelarServidor() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor, "Gestão " + UUID.randomUUID());
        Unidade sec = unidadeRepository.saveAndFlush(
                new Unidade("Sec-" + UUID.randomUUID(), TipoUnidade.SECRETARIA, null));

        postar(serv, "/gestao/denuncias/" + d.getId() + "/prioridade", "prioridade", "URGENTE")
                .andExpect(flash().attributeExists("mensagem"));
        postar(serv, "/gestao/denuncias/" + d.getId() + "/encaminhar", "unidadeId", String.valueOf(sec.getId()),
                "observacao", "nota interna sigilosa").andExpect(flash().attributeExists("mensagem"));

        List<Notificacao> lista = de(autor);
        assertThat(lista).hasSize(2).extracting(Notificacao::getTipo)
                .containsOnly(TipoNotificacao.ADMINISTRATIVA);
        assertThat(lista).anyMatch(n -> n.getMensagem().contains("URGENTE"));
        assertThat(lista).anyMatch(n -> n.getMensagem().contains(sec.getNome()));
        assertThat(lista).noneMatch(n -> n.getMensagem().contains("nota interna")
                || n.getMensagem().contains(serv.getName()));
    }

    @Test
    void agrupamentoEDesagrupamentoNotificamOAutorDaDuplicada() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User outroAutor = usuario("Outro", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia principal = denuncia(outroAutor, "Principal " + UUID.randomUUID());
        Denuncia filha = denuncia(autor, "Filha " + UUID.randomUUID());

        postar(serv, "/gestao/denuncias/" + filha.getId() + "/agrupar",
                "principalId", String.valueOf(principal.getId()), "justificativa", "Mesmo problema");
        postar(serv, "/gestao/denuncias/" + filha.getId() + "/desagrupar", "justificativa", "Eram distintos");

        List<Notificacao> lista = de(autor);
        assertThat(lista).hasSize(2).extracting(Notificacao::getTipo).containsOnly(TipoNotificacao.AGRUPAMENTO);
        assertThat(lista).anyMatch(n -> n.getMensagem().contains("agrupada à #" + principal.getId()));
        assertThat(lista).anyMatch(n -> n.getMensagem().contains("desagrupada"));
        assertThat(de(outroAutor)).isEmpty();
    }

    @Test
    void ocultarERestaurarNotificamComOMotivo() throws Exception {
        User admin = usuario("Admin", Role.ADMINISTRADOR);
        User autor = usuario("Autor", Role.CIDADAO);
        User comentarista = usuario("Comentarista", Role.CIDADAO);
        Denuncia d = denuncia(autor, "Moderada " + UUID.randomUUID());
        Long comentarioId = jdbc.queryForObject(
                "INSERT INTO comentarios (denuncia_id, usuario_id, conteudo) VALUES (?, ?, 'texto') RETURNING id",
                Long.class, d.getId(), comentarista.getId());

        postar(admin, "/admin/moderacao/conteudo/DENUNCIA/" + d.getId() + "/ocultar",
                "justificativa", "Conteúdo ofensivo");
        postar(admin, "/admin/moderacao/conteudo/COMENTARIO/" + comentarioId + "/ocultar",
                "justificativa", "Spam evidente");
        postar(admin, "/admin/moderacao/conteudo/DENUNCIA/" + d.getId() + "/restaurar",
                "justificativa", "Engano");

        List<Notificacao> doAutor = de(autor);
        assertThat(doAutor).hasSize(2).extracting(Notificacao::getTipo).containsOnly(TipoNotificacao.MODERACAO);
        assertThat(doAutor).anyMatch(n -> n.getMensagem().contains("ocultada")
                && n.getMensagem().contains("Conteúdo ofensivo"));
        assertThat(doAutor).anyMatch(n -> n.getMensagem().contains("restaurada"));

        List<Notificacao> doComentarista = de(comentarista);
        assertThat(doComentarista).hasSize(1);
        assertThat(doComentarista.get(0).getMensagem()).contains("comentário").contains("Spam evidente");
        assertThat(de(admin)).isEmpty();
    }

    @Test
    void cadaPessoaVeSoAsPropriasNotificacoes() throws Exception {
        User a = usuario("A", Role.CIDADAO);
        User b = usuario("B", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia da = denuncia(a, "DeA " + UUID.randomUUID());
        Denuncia db = denuncia(b, "DeB " + UUID.randomUUID());
        mudarStatus(serv, da.getId(), "EM_ANALISE", "ok");
        mudarStatus(serv, db.getId(), "EM_ANALISE", "ok");
        limpar();

        String paginaDeA = pagina("/notificacoes", a);

        assertThat(paginaDeA).contains(da.getTitulo()).doesNotContain(db.getTitulo());
    }

    @Test
    void marcarComoLidaSoFuncionaNasProprias() throws Exception {
        User a = usuario("A", Role.CIDADAO);
        User b = usuario("B", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia da = denuncia(a, "DeA " + UUID.randomUUID());
        mudarStatus(serv, da.getId(), "EM_ANALISE", "ok");
        Long id = de(a).get(0).getId();

        postar(b, "/notificacoes/" + id + "/lida").andExpect(status().is3xxRedirection());
        assertThat(de(a).get(0).isLida()).isFalse();

        postar(a, "/notificacoes/" + id + "/lida").andExpect(status().is3xxRedirection());
        assertThat(de(a).get(0).isLida()).isTrue();
    }

    @Test
    void marcarTodasComoLidasEContadorDoMenu() throws Exception {
        User a = usuario("A", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d1 = denuncia(a, "Um " + UUID.randomUUID());
        Denuncia d2 = denuncia(a, "Dois " + UUID.randomUUID());
        mudarStatus(serv, d1.getId(), "EM_ANALISE", "ok");
        mudarStatus(serv, d2.getId(), "EM_ANALISE", "ok");
        limpar();

        assertThat(notificacaoService.naoLidas(a.getEmail())).isEqualTo(2);
        assertThat(pagina("/denuncias", a)).contains("id=\"nav-nao-lidas\"");

        postar(a, "/notificacoes/lidas").andExpect(status().is3xxRedirection());
        limpar();

        assertThat(notificacaoService.naoLidas(a.getEmail())).isZero();
        assertThat(pagina("/denuncias", a)).doesNotContain("id=\"nav-nao-lidas\"");
    }

    @Test
    void exigeLoginEToken() throws Exception {
        mvc().perform(get("/notificacoes"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        User a = usuario("A", Role.CIDADAO);
        mvc().perform(post("/notificacoes/lidas").with(user(a.getEmail()).roles("CIDADAO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void tituloComHtmlApareceEscapado() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor, "<b>negrito</b>");
        mudarStatus(serv, d.getId(), "EM_ANALISE", "ok");
        limpar();

        String html = pagina("/notificacoes", autor);

        assertThat(html).contains("&lt;b&gt;negrito&lt;/b&gt;").doesNotContain("<b>negrito</b>");
    }
}