package com.vozpublica.vozpublica.relatorio;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.denuncia.Prioridade;
import com.vozpublica.vozpublica.relatorio.RelatorioService.Painel;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class TransparenciaTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private TransparenciaService service;
    @Autowired private RelatorioService relatorioService;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User autor() {
        return userRepository.saveAndFlush(new User("Autora Transparente",
                "transp." + UUID.randomUUID() + "@teste.com", "hash", Role.CIDADAO));
    }

    /** Cria no ano informado (isolado dos dados reais) e com a situação pedida. */
    private Denuncia criar(User autor, int ano, String bairro, String status) {
        Denuncia d = denunciaRepository.saveAndFlush(new Denuncia(autor,
                categoriaRepository.findByAtivaTrueOrderByNome().get(0), "Ocorrência " + UUID.randomUUID(),
                "Descrição de teste do problema.", null, bairro, "RPA 1 - Centro",
                null, null, Prioridade.MEDIA));
        jdbc.update("UPDATE denuncias SET data_criacao = ?, status = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(ano, 5, 10, 12, 0)), status, d.getId());
        return d;
    }

    private void limpar() {
        em.flush();
        em.clear();
    }

    private Painel painel(int ano) {
        limpar();
        return service.painel(LocalDate.of(ano, 1, 1), LocalDate.of(ano, 12, 31));
    }

    @Test
    void paginaEPublica() throws Exception {
        mvc().perform(get("/transparencia"))
                .andExpect(status().isOk())
                .andExpect(view().name("transparencia"))
                .andExpect(model().attributeExists("painel"));
    }

    @Test
    void contaCadaSituacaoEMostraOsPercentuais() {
        User a = autor();
        String b = "B-" + UUID.randomUUID();
        for (String s : List.of("RECEBIDA", "REABERTA", "EM_ANALISE", "ENCAMINHADA",
                "EM_EXECUCAO", "RESOLVIDA", "INDEFERIDA")) {
            criar(a, 2001, b, s);
        }

        var g = painel(2001).geral();

        assertThat(g.total()).isEqualTo(7);
        assertThat(g.pendentes()).isEqualTo(2);
        assertThat(g.emAnalise() + g.encaminhadas()).isEqualTo(2);
        assertThat(g.emExecucao()).isEqualTo(1);
        assertThat(g.resolvidas()).isEqualTo(1);
        assertThat(g.indeferidas()).isEqualTo(1);
        assertThat(g.percentualResolucao()).isBetween(14.2, 14.3);
    }

    @Test
    void ocultasEDuplicadasNaoSaoContadas() {
        User a = autor();
        String b = "B-" + UUID.randomUUID();
        Denuncia principal = criar(a, 2002, b, "RECEBIDA");
        Denuncia oculta = criar(a, 2002, b, "RECEBIDA");
        Denuncia duplicada = criar(a, 2002, b, "RECEBIDA");
        jdbc.update("UPDATE denuncias SET oculta = TRUE WHERE id = ?", oculta.getId());
        jdbc.update("UPDATE denuncias SET duplicada_de_id = ? WHERE id = ?", principal.getId(), duplicada.getId());

        assertThat(painel(2002).geral().total()).isEqualTo(1);
    }

    @Test
    void soOPeriodoFiltraEOutrosParametrosSaoIgnorados() throws Exception {
        User a = autor();
        criar(a, 2003, "B-" + UUID.randomUUID(), "RECEBIDA");
        criar(a, 2003, "B-" + UUID.randomUUID(), "RESOLVIDA");
        limpar();

        Painel p = (Painel) mvc().perform(get("/transparencia")
                        .param("de", "2003-01-01").param("ate", "2003-12-31")
                        .param("bairro", "inexistente").param("categoriaId", "1")
                        .param("regiao", "RPA 6 - Sul").param("unidadeId", "1"))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("painel");

        assertThat(p.geral().total()).isEqualTo(2);
    }

    @Test
    void datasInvalidasSaoIgnoradasSemErro() throws Exception {
        mvc().perform(get("/transparencia").param("de", "2020-13-45").param("ate", "abc"))
                .andExpect(status().isOk());
    }

    @Test
    void paginaNaoExpoeDadosPessoaisEEscapaOBairro() throws Exception {
        User a = autor();
        String bairro = "<b>negrito</b>-" + UUID.randomUUID();
        criar(a, 2004, bairro, "RECEBIDA");
        limpar();

        String html = mvc().perform(get("/transparencia")
                        .param("de", "2004-01-01").param("ate", "2004-12-31"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("&lt;b&gt;negrito&lt;/b&gt;").doesNotContain("<b>negrito</b>");
        assertThat(html).doesNotContain(a.getEmail()).doesNotContain(a.getName());
        assertThat(html).contains("Não são estatísticas oficiais");
    }

    @Test
    void resultadoFicaEmCachePorUmMinutoPorPeriodo() {
        User a = autor();
        String b = "B-" + UUID.randomUUID();
        criar(a, 2005, b, "RECEBIDA");

        Painel primeiro = painel(2005);
        criar(a, 2005, b, "RECEBIDA");
        Painel segundo = painel(2005);

        assertThat(segundo).isSameAs(primeiro);
        assertThat(segundo.geral().total()).isEqualTo(1);
    }

    @Test
    void semDadosOsNumerosVoltamZeradosSemErro() {
        var g = painel(2006).geral();

        assertThat(g.total()).isZero();
        assertThat(g.tempoMedioDias()).isNull();
        assertThat(g.tempoMedioTexto()).isEqualTo("—");
    }

    @Test
    @WithMockUser(roles = "CIDADAO")
    void oPainelDaEquipeContinuaProtegido() {
        assertThatThrownBy(() -> relatorioService.painel(new FiltroRelatorio()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void menuTemOLinkParaVisitantes() throws Exception {
        String html = mvc().perform(get("/denuncias")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("/transparencia");
    }
}