package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.auditoria.AcaoAuditoria;
import com.vozpublica.vozpublica.auditoria.AuditoriaRepository;
import com.vozpublica.vozpublica.auditoria.RegistroAuditoria;
import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.encaminhamento.AtrasoRepository;
import com.vozpublica.vozpublica.encaminhamento.DenunciaAtrasada;
import com.vozpublica.vozpublica.encaminhamento.EncaminhamentoRepository;
import com.vozpublica.vozpublica.interacao.IncentivoRepository;
import com.vozpublica.vozpublica.interacao.InteracaoService;
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
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AgrupamentoTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private AgrupamentoRepository agrupamentoRepository;
    @Autowired private AuditoriaRepository auditoriaRepository;
    @Autowired private IncentivoRepository incentivoRepository;
    @Autowired private InteracaoService interacaoService;
    @Autowired private DenunciaConsultaService consulta;
    @Autowired private SimilaridadeService similaridade;
    @Autowired private AgrupamentoService agrupamentoService;
    @Autowired private AtrasoRepository atrasoRepository;
    @Autowired private EncaminhamentoRepository encaminhamentoRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User usuario(String prefixo, Role papel) {
        return userRepository.saveAndFlush(new User("Teste " + prefixo,
                prefixo + "." + UUID.randomUUID() + "@teste.com", "hash", papel));
    }

    private Denuncia denuncia(User autor, String bairro) {
        return denunciaRepository.saveAndFlush(new Denuncia(autor,
                categoriaRepository.findByAtivaTrueOrderByNome().get(0),
                "Ocorrência " + UUID.randomUUID(), "Descrição de teste do problema.",
                null, bairro, "RPA 1 - Centro", null, null, Prioridade.MEDIA));
    }

    private void incentivo(Denuncia d, User u) {
        jdbc.update("INSERT INTO incentivos (denuncia_id, usuario_id) VALUES (?, ?)", d.getId(), u.getId());
    }

    private void limpar() {
        em.flush();
        em.clear();
    }

    private Denuncia reler(Long id) {
        limpar();
        return denunciaRepository.findById(id).orElseThrow();
    }

    private ResultActions postar(User quem, Role papel, String caminho, String... pares) throws Exception {
        var b = post(caminho).with(user(quem.getEmail()).roles(papel.name())).with(csrf());
        for (int i = 0; i < pares.length; i += 2) {
            b.param(pares[i], pares[i + 1]);
        }
        return mvc().perform(b);
    }

    private ResultActions agrupar(User servidor, Long filha, Long principal, String motivo) throws Exception {
        return postar(servidor, Role.SERVIDOR, "/gestao/denuncias/" + filha + "/agrupar",
                "principalId", String.valueOf(principal), "justificativa", motivo);
    }

    private List<RegistroAuditoria> auditoria(AcaoAuditoria acao, Long id) {
        limpar();
        return auditoriaRepository.findAll().stream()
                .filter(a -> a.getAcao() == acao && "DENUNCIA".equals(a.getEntidade())
                        && Objects.equals(a.getEntidadeId(), id)).toList();
    }

    private String html(String url, User quem, Role papel) throws Exception {
        var req = get(url);
        if (quem != null) req.with(user(quem.getEmail()).roles(papel.name()));
        return mvc().perform(req).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void soAEquipeAgrupa() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        Denuncia p = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia f = denuncia(autor, "B-" + UUID.randomUUID());
        String url = "/gestao/denuncias/" + f.getId() + "/agrupar";

        mvc().perform(post(url).with(csrf()).param("principalId", String.valueOf(p.getId()))
                        .param("justificativa", "x"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        postar(autor, Role.CIDADAO, url, "principalId", String.valueOf(p.getId()), "justificativa", "x")
                .andExpect(status().isForbidden());

        assertThat(reler(f.getId()).getDuplicadaDeId()).isNull();
    }

    @Test
    void servidorAgrupaEHistoricoEAuditoriaFicamRegistrados() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        Denuncia p = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia f = denuncia(autor, "B-" + UUID.randomUUID());

        agrupar(serv, f.getId(), p.getId(), "  Mesmo buraco  ")
                .andExpect(flash().attributeExists("mensagem"));

        assertThat(reler(f.getId()).getDuplicadaDeId()).isEqualTo(p.getId());
        assertThat(reler(p.getId()).getDuplicadaDeId()).isNull();

        List<Agrupamento> hist = agrupamentoRepository.findByDenunciaIdOrderByIdDesc(f.getId());
        assertThat(hist).hasSize(1);
        assertThat(hist.get(0).getTipo()).isEqualTo(TipoAgrupamento.AGRUPADA);
        assertThat(hist.get(0).getPrincipalId()).isEqualTo(p.getId());
        assertThat(hist.get(0).getJustificativa()).isEqualTo("Mesmo buraco");
        assertThat(hist.get(0).getResponsavel().getEmail()).isEqualTo(serv.getEmail());

        List<RegistroAuditoria> aud = auditoria(AcaoAuditoria.DUPLICIDADE_AGRUPADA, f.getId());
        assertThat(aud).hasSize(1);
        assertThat(aud.get(0).getJustificativa()).isEqualTo("Mesmo buraco");
        assertThat(aud.get(0).getDetalhes()).contains("#" + f.getId()).contains("#" + p.getId());
    }

    @Test
    void justificativaEObrigatoria() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        Denuncia p = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia f = denuncia(autor, "B-" + UUID.randomUUID());

        agrupar(serv, f.getId(), p.getId(), "   ").andExpect(flash().attributeExists("erro"));

        assertThat(reler(f.getId()).getDuplicadaDeId()).isNull();
        assertThat(agrupamentoRepository.findByDenunciaIdOrderByIdDesc(f.getId())).isEmpty();
    }

    @Test
    void naoAgrupaConsigoMesmoNemComPrincipalInexistente() throws Exception {
        User serv = usuario("serv", Role.SERVIDOR);
        Denuncia f = denuncia(usuario("autor", Role.CIDADAO), "B-" + UUID.randomUUID());

        agrupar(serv, f.getId(), f.getId(), "x").andExpect(flash().attributeExists("erro"));
        agrupar(serv, f.getId(), 999_999_999L, "x").andExpect(flash().attributeExists("erro"));

        assertThat(reler(f.getId()).getDuplicadaDeId()).isNull();
    }

    @Test
    void principalQueJaEDuplicadaEhRejeitada() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        Denuncia a = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia b = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia c = denuncia(autor, "B-" + UUID.randomUUID());

        agrupar(serv, b.getId(), a.getId(), "ok").andExpect(flash().attributeExists("mensagem"));
        agrupar(serv, c.getId(), b.getId(), "cadeia").andExpect(flash().attributeExists("erro"));

        assertThat(reler(c.getId()).getDuplicadaDeId()).isNull();
    }

    @Test
    void ocorrenciaQueJaReuneDuplicadasNaoViraDuplicada() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        Denuncia a = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia b = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia c = denuncia(autor, "B-" + UUID.randomUUID());

        agrupar(serv, b.getId(), a.getId(), "ok").andExpect(flash().attributeExists("mensagem"));
        agrupar(serv, a.getId(), c.getId(), "inverte").andExpect(flash().attributeExists("erro"));

        assertThat(reler(a.getId()).getDuplicadaDeId()).isNull();
    }

    @Test
    void duplicadaJaAgrupadaNaoPodeSerAgrupadaDeNovoSemDesagrupar() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        Denuncia a = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia b = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia c = denuncia(autor, "B-" + UUID.randomUUID());

        agrupar(serv, b.getId(), a.getId(), "ok").andExpect(flash().attributeExists("mensagem"));
        agrupar(serv, b.getId(), c.getId(), "outra").andExpect(flash().attributeExists("erro"));

        assertThat(reler(b.getId()).getDuplicadaDeId()).isEqualTo(a.getId());
    }

    @Test
    void ocorrenciasOcultasNaoSaoAgrupadas() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        Denuncia p = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia f = denuncia(autor, "B-" + UUID.randomUUID());

        jdbc.update("UPDATE denuncias SET oculta = TRUE WHERE id = ?", p.getId());
        limpar();
        agrupar(serv, f.getId(), p.getId(), "x").andExpect(flash().attributeExists("erro"));

        jdbc.update("UPDATE denuncias SET oculta = FALSE WHERE id = ?", p.getId());
        jdbc.update("UPDATE denuncias SET oculta = TRUE WHERE id = ?", f.getId());
        limpar();
        agrupar(serv, f.getId(), p.getId(), "x").andExpect(flash().attributeExists("erro"));

        assertThat(reler(f.getId()).getDuplicadaDeId()).isNull();
    }

    @Test
    void bancoBarraPrincipalQueJaEDuplicada() {
        User autor = usuario("autor", Role.CIDADAO);
        Denuncia a = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia b = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia c = denuncia(autor, "B-" + UUID.randomUUID());
        jdbc.update("UPDATE denuncias SET duplicada_de_id = ? WHERE id = ?", a.getId(), b.getId());

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE denuncias SET duplicada_de_id = ? WHERE id = ?", b.getId(), c.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("duplicada");
    }

    @Test
    void bancoBarraDuplicadaQueJaTemDuplicadas() {
        User autor = usuario("autor", Role.CIDADAO);
        Denuncia a = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia b = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia c = denuncia(autor, "B-" + UUID.randomUUID());
        jdbc.update("UPDATE denuncias SET duplicada_de_id = ? WHERE id = ?", a.getId(), b.getId());

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE denuncias SET duplicada_de_id = ? WHERE id = ?", c.getId(), a.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("duplicada");
    }

    @Test
    void incentivosSaoConsolidadosSemContarDuasVezesAMesmaPessoaEOsOriginaisSaoPreservados() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        User u1 = usuario("u1", Role.CIDADAO);
        User u2 = usuario("u2", Role.CIDADAO);
        String bairro = "B-" + UUID.randomUUID();
        Denuncia p = denuncia(autor, bairro);
        Denuncia f = denuncia(autor, bairro);
        incentivo(p, u1);
        incentivo(f, u1);
        incentivo(f, u2);
        limpar();
        assertThat(incentivoRepository.contarConsolidado(p.getId())).isEqualTo(1);

        agrupar(serv, f.getId(), p.getId(), "Mesmo problema").andExpect(flash().attributeExists("mensagem"));
        limpar();

        assertThat(incentivoRepository.contarConsolidado(p.getId())).isEqualTo(2); // u1 conta uma vez
        assertThat(incentivoRepository.contarConsolidado(f.getId())).isEqualTo(2);
        assertThat(incentivoRepository.countByDenunciaId(f.getId())).isEqualTo(2); // linhas preservadas
        assertThat(incentivoRepository.countByDenunciaId(p.getId())).isEqualTo(1);
    }

    @Test
    void listagemEscondeADuplicadaEMostraOApoioConsolidadoNaPrincipal() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        User u1 = usuario("u1", Role.CIDADAO);
        User u2 = usuario("u2", Role.CIDADAO);
        String bairro = "B-" + UUID.randomUUID();
        Denuncia p = denuncia(autor, bairro);
        Denuncia f = denuncia(autor, bairro);
        incentivo(p, u1);
        incentivo(f, u2);
        limpar();
        FiltroDenuncia filtro = new FiltroDenuncia();
        filtro.setBairro(bairro);
        assertThat(consulta.buscar(filtro, 0).page().getContent()).hasSize(2);

        agrupar(serv, f.getId(), p.getId(), "Mesmo problema");
        limpar();

        var r = consulta.buscar(filtro, 0);
        assertThat(r.page().getContent()).extracting(Denuncia::getId).containsExactly(p.getId());
        assertThat(r.contadores().get(p.getId()).incentivos()).isEqualTo(2);
    }

    @Test
    void ordenacaoPorIncentivadasUsaAContagemConsolidada() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        User u1 = usuario("u1", Role.CIDADAO);
        User u2 = usuario("u2", Role.CIDADAO);
        User u3 = usuario("u3", Role.CIDADAO);
        String bairro = "B-" + UUID.randomUUID();
        Denuncia p1 = denuncia(autor, bairro);
        Denuncia filha = denuncia(autor, bairro);
        Denuncia p2 = denuncia(autor, bairro);
        incentivo(p1, u1);       // p1 próprio: 1
        incentivo(filha, u2);
        incentivo(filha, u3);    // p1 consolidado: 3
        incentivo(p2, u1);
        incentivo(p2, u2);       // p2: 2
        limpar();

        agrupar(serv, filha.getId(), p1.getId(), "Mesmo problema");
        limpar();

        FiltroDenuncia filtro = new FiltroDenuncia();
        filtro.setBairro(bairro);
        filtro.setOrdem(FiltroDenuncia.Ordem.INCENTIVADAS);
        assertThat(consulta.buscar(filtro, 0).page().getContent())
                .extracting(Denuncia::getId).containsExactly(p1.getId(), p2.getId());
    }

    @Test
    void resumoConsideraQuemApoiouUmaDuplicadaComoQuemIncentivouAPrincipal() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        User u1 = usuario("u1", Role.CIDADAO);
        String bairro = "B-" + UUID.randomUUID();
        Denuncia p = denuncia(autor, bairro);
        Denuncia f = denuncia(autor, bairro);
        incentivo(f, u1);
        limpar();
        assertThat(interacaoService.resumo(p.getId(), u1.getEmail()).incentivou()).isFalse();

        agrupar(serv, f.getId(), p.getId(), "Mesmo problema");
        limpar();

        var resumo = interacaoService.resumo(p.getId(), u1.getEmail());
        assertThat(resumo.incentivou()).isTrue();
        assertThat(resumo.incentivos()).isEqualTo(1);
    }

    @Test
    void duplicadaSaiDasAtrasadasEDasSugestoes() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        String bairro = "B-" + UUID.randomUUID();
        Denuncia p = denuncia(autor, bairro);
        Denuncia f = denuncia(autor, bairro);
        jdbc.update("UPDATE denuncias SET data_criacao = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2020, 1, 1, 12, 0)), f.getId());
        limpar();
        Long categoriaId = categoriaRepository.findByAtivaTrueOrderByNome().get(0).getId();

        assertThat(idsAtrasadas()).contains(f.getId());
        assertThat(similaridade.buscar(categoriaId, bairro, "problema", null, null))
                .extracting(SimilaridadeService.Similar::id).contains(f.getId(), p.getId());

        agrupar(serv, f.getId(), p.getId(), "Mesmo problema");
        limpar();

        assertThat(idsAtrasadas()).doesNotContain(f.getId());
        assertThat(similaridade.buscar(categoriaId, bairro, "problema", null, null))
                .extracting(SimilaridadeService.Similar::id).contains(p.getId()).doesNotContain(f.getId());
    }

    private List<Long> idsAtrasadas() {
        return atrasoRepository.buscarAtrasadas(LocalDate.now(), PageRequest.of(0, 100_000))
                .getContent().stream().map(DenunciaAtrasada::getId).toList();
    }

    @Test
    void duplicadaNaoRecebeInteracoesMasSuaPaginaAbreComAvisoEsemBotoes() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        User cidadao = usuario("cidadao", Role.CIDADAO);
        Denuncia p = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia f = denuncia(autor, "B-" + UUID.randomUUID());
        agrupar(serv, f.getId(), p.getId(), "Mesmo problema");

        String base = "/denuncias/" + f.getId();
        postar(cidadao, Role.CIDADAO, base + "/curtir").andExpect(status().isNotFound());
        postar(cidadao, Role.CIDADAO, base + "/incentivar").andExpect(status().isNotFound());
        postar(cidadao, Role.CIDADAO, base + "/comentarios", "conteudo", "Olá")
                .andExpect(status().isNotFound());

        String pagina = html(base, cidadao, Role.CIDADAO);
        assertThat(pagina).contains("foi agrupada à principal").contains("/denuncias/" + p.getId());
        assertThat(pagina).doesNotContain(base + "/curtir")
                .doesNotContain(base + "/incentivar")
                .doesNotContain(base + "/comentarios");
    }

    @Test
    void paginaDaPrincipalListaAsDuplicadasAgrupadas() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        Denuncia p = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia f = denuncia(autor, "B-" + UUID.randomUUID());
        agrupar(serv, f.getId(), p.getId(), "Mesmo problema");
        limpar();

        String pagina = html("/denuncias/" + p.getId(), null, null);
        assertThat(pagina).contains("Ocorrências agrupadas a esta").contains(f.getTitulo());
    }

    @Test
    void gestaoDaDuplicadaEBloqueadaEPassaPelaPrincipal() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        Denuncia p = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia f = denuncia(autor, "B-" + UUID.randomUUID());
        Unidade sec = unidadeRepository.saveAndFlush(
                new Unidade("Sec-" + UUID.randomUUID(), TipoUnidade.SECRETARIA, null));
        agrupar(serv, f.getId(), p.getId(), "Mesmo problema");

        MockMultipartHttpServletRequestBuilder status =
                multipart("/gestao/denuncias/{id}/status", f.getId());
        status.with(user(serv.getEmail()).roles("SERVIDOR")).with(csrf())
                .param("novoStatus", "EM_ANALISE").param("observacao", "tentativa");
        mvc().perform(status).andExpect(flash().attributeExists("erro"));

        postar(serv, Role.SERVIDOR, "/gestao/denuncias/" + f.getId() + "/prioridade", "prioridade", "ALTA")
                .andExpect(flash().attributeExists("erro"));
        postar(serv, Role.SERVIDOR, "/gestao/denuncias/" + f.getId() + "/encaminhar",
                "unidadeId", String.valueOf(sec.getId()))
                .andExpect(flash().attributeExists("erro"));

        Denuncia relida = reler(f.getId());
        assertThat(relida.getStatus()).isEqualTo(StatusDenuncia.RECEBIDA);
        assertThat(relida.getPrioridadeOficial()).isNull();
        assertThat(encaminhamentoRepository.findByDenunciaIdOrderByIdDesc(f.getId())).isEmpty();
    }

    @Test
    void desagruparRestauraAOcorrenciaEMantemOHistorico() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        Denuncia p = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia f = denuncia(autor, "B-" + UUID.randomUUID());
        String url = "/gestao/denuncias/" + f.getId() + "/desagrupar";

        postar(serv, Role.SERVIDOR, url, "justificativa", "x")
                .andExpect(flash().attributeExists("erro")); // ainda não está agrupada

        agrupar(serv, f.getId(), p.getId(), "Mesmo problema");

        postar(serv, Role.SERVIDOR, url, "justificativa", "  ").andExpect(flash().attributeExists("erro"));
        assertThat(reler(f.getId()).getDuplicadaDeId()).isEqualTo(p.getId());

        postar(serv, Role.SERVIDOR, url, "justificativa", "Eram problemas distintos")
                .andExpect(flash().attributeExists("mensagem"));

        assertThat(reler(f.getId()).getDuplicadaDeId()).isNull();
        assertThat(agrupamentoRepository.findByDenunciaIdOrderByIdDesc(f.getId()))
                .extracting(Agrupamento::getTipo)
                .containsExactly(TipoAgrupamento.DESAGRUPADA, TipoAgrupamento.AGRUPADA);
        assertThat(auditoria(AcaoAuditoria.DUPLICIDADE_DESAGRUPADA, f.getId())).hasSize(1);

        postar(serv, Role.SERVIDOR, url, "justificativa", "de novo")
                .andExpect(flash().attributeExists("erro"));
    }

    @Test
    void registroDeAgrupamentoNaoPodeSerAlteradoPorSql() {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        Denuncia p = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia f = denuncia(autor, "B-" + UUID.randomUUID());
        Agrupamento a = agrupamentoRepository.saveAndFlush(
                new Agrupamento(f.getId(), p.getId(), TipoAgrupamento.AGRUPADA, serv, "original"));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE agrupamentos SET justificativa = 'alterada' WHERE id = ?", a.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @WithMockUser(roles = "CIDADAO")
    void servicoRecusaChamadaDeCidadaoMesmoSemPassarPeloControlador() {
        assertThatThrownBy(() -> agrupamentoService.agrupar(1L, 2L, "x", "a@teste.com"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> agrupamentoService.desagrupar(1L, "x", "a@teste.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void painelDaEquipeMostraAsDuplicidades() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User serv = usuario("serv", Role.SERVIDOR);
        Denuncia p = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia f = denuncia(autor, "B-" + UUID.randomUUID());
        agrupar(serv, f.getId(), p.getId(), "Mesmo problema");
        limpar();

        mvc().perform(get("/gestao/denuncias/{id}", p.getId()).with(user(serv.getEmail()).roles("SERVIDOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("gestao-denuncia"))
                .andExpect(model().attributeExists("agrupada", "filhas", "historicoAgrupamento"));

        String painelFilha = html("/gestao/denuncias/" + f.getId(), serv, Role.SERVIDOR);
        assertThat(painelFilha).contains("Desagrupar").contains("Mesmo problema");
    }
}