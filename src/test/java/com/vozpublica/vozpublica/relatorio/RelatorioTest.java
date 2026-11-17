package com.vozpublica.vozpublica.relatorio;

import com.vozpublica.vozpublica.auditoria.AcaoAuditoria;
import com.vozpublica.vozpublica.auditoria.AuditoriaRepository;
import com.vozpublica.vozpublica.auditoria.RegistroAuditoria;
import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.denuncia.HistoricoStatus;
import com.vozpublica.vozpublica.denuncia.HistoricoStatusRepository;
import com.vozpublica.vozpublica.denuncia.Prioridade;
import com.vozpublica.vozpublica.denuncia.StatusDenuncia;
import com.vozpublica.vozpublica.encaminhamento.AtrasoRepository;
import com.vozpublica.vozpublica.encaminhamento.DenunciaAtrasada;
import com.vozpublica.vozpublica.encaminhamento.Encaminhamento;
import com.vozpublica.vozpublica.encaminhamento.EncaminhamentoRepository;
import com.vozpublica.vozpublica.relatorio.RelatorioService.Indicadores;
import com.vozpublica.vozpublica.relatorio.RelatorioService.Linha;
import com.vozpublica.vozpublica.unidade.TipoUnidade;
import com.vozpublica.vozpublica.unidade.Unidade;
import com.vozpublica.vozpublica.unidade.UnidadeRepository;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class RelatorioTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private HistoricoStatusRepository historicoRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EncaminhamentoRepository encaminhamentoRepository;
    @Autowired private AuditoriaRepository auditoriaRepository;
    @Autowired private AtrasoRepository atrasoRepository;
    @Autowired private RelatorioService service;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User usuario(String prefixo, Role papel) {
        return userRepository.saveAndFlush(new User("Teste " + prefixo,
                prefixo + "." + UUID.randomUUID() + "@teste.com", "hash", papel));
    }

    private Denuncia criar(User autor, String bairro, String titulo) {
        return denunciaRepository.saveAndFlush(new Denuncia(autor,
                categoriaRepository.findByAtivaTrueOrderByNome().get(0), titulo,
                "Descrição de teste do problema.", null, bairro, "RPA 1 - Centro",
                null, null, Prioridade.MEDIA));
    }

    private Denuncia comStatus(User autor, String bairro, String status) {
        Denuncia d = criar(autor, bairro, "Ocorrência " + UUID.randomUUID());
        jdbc.update("UPDATE denuncias SET status = ? WHERE id = ?", status, d.getId());
        return d;
    }

    private void envelhecer(Denuncia d) {
        jdbc.update("UPDATE denuncias SET data_criacao = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2020, 1, 1, 12, 0)), d.getId());
    }

    private FiltroRelatorio filtro(String bairro) {
        FiltroRelatorio f = new FiltroRelatorio();
        f.setBairro(bairro);
        return f;
    }

    private String csv(String url, User quem) throws Exception {
        MvcResult r = mvc().perform(get(url).with(user(quem.getEmail()).roles(quem.getRole().name())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        assertThat(r.getResponse().getContentType()).startsWith("text/csv");
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void soAEquipeAcessaPaginasEExportacoes() throws Exception {
        User cidadao = usuario("cidadao", Role.CIDADAO);
        User servidor = usuario("serv", Role.SERVIDOR);

        for (String url : new String[]{"/gestao/dashboard", "/gestao/relatorios",
                "/gestao/relatorios/agrupado.csv", "/gestao/relatorios/ocorrencias.csv"}) {
            mvc().perform(get(url))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
            mvc().perform(get(url).with(user(cidadao.getEmail()).roles("CIDADAO")))
                    .andExpect(status().isForbidden());
            mvc().perform(get(url).with(user(servidor.getEmail()).roles("SERVIDOR")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(roles = "CIDADAO")
    void servicoRecusaChamadaDeCidadaoMesmoSemPassarPeloControlador() {
        assertThatThrownBy(() -> service.painel(new FiltroRelatorio()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.exportarOcorrencias(new FiltroRelatorio(), "a@teste.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "SERVIDOR")
    void indicadoresContamCadaSituacao() {
        String bairro = "B-" + UUID.randomUUID();
        User autor = usuario("autor", Role.CIDADAO);
        for (String s : List.of("RECEBIDA", "REABERTA", "EM_ANALISE", "ENCAMINHADA",
                "EM_EXECUCAO", "RESOLVIDA", "INDEFERIDA")) {
            comStatus(autor, bairro, s);
        }

        Indicadores i = service.painel(filtro(bairro)).geral();

        assertThat(i.total()).isEqualTo(7);
        assertThat(i.pendentes()).isEqualTo(2);
        assertThat(i.emAnalise()).isEqualTo(1);
        assertThat(i.encaminhadas()).isEqualTo(1);
        assertThat(i.emExecucao()).isEqualTo(1);
        assertThat(i.resolvidas()).isEqualTo(1);
        assertThat(i.indeferidas()).isEqualTo(1);
        assertThat(i.atrasadas()).isZero();
        assertThat(i.percentualResolucao()).isBetween(14.2, 14.3);
    }

    @Test
    @WithMockUser(roles = "SERVIDOR")
    void ocultasEDuplicadasFicamFora() {
        String bairro = "B-" + UUID.randomUUID();
        User autor = usuario("autor", Role.CIDADAO);
        Denuncia principal = criar(autor, bairro, "Principal");
        Denuncia oculta = criar(autor, bairro, "Oculta");
        Denuncia duplicada = criar(autor, bairro, "Duplicada");
        jdbc.update("UPDATE denuncias SET oculta = TRUE WHERE id = ?", oculta.getId());
        jdbc.update("UPDATE denuncias SET duplicada_de_id = ? WHERE id = ?", principal.getId(), duplicada.getId());

        assertThat(service.painel(filtro(bairro)).geral().total()).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "SERVIDOR")
    void atrasadasConcordamComAListaDeAtrasadas() {
        String bairro = "B-" + UUID.randomUUID();
        User autor = usuario("autor", Role.CIDADAO);
        Denuncia atrasada1 = comStatus(autor, bairro, "EM_ANALISE");
        Denuncia atrasada2 = comStatus(autor, bairro, "RECEBIDA");
        Denuncia resolvida = comStatus(autor, bairro, "RESOLVIDA");
        Denuncia recente = comStatus(autor, bairro, "RECEBIDA");
        envelhecer(atrasada1);
        envelhecer(atrasada2);
        envelhecer(resolvida);

        List<Long> idsDaLista = atrasoRepository.buscarAtrasadas(LocalDate.now(), PageRequest.of(0, 100_000))
                .getContent().stream().map(DenunciaAtrasada::getId).toList();
        long viaLista = Stream.of(atrasada1, atrasada2, resolvida, recente)
                .filter(d -> idsDaLista.contains(d.getId())).count();

        assertThat(service.painel(filtro(bairro)).geral().atrasadas()).isEqualTo(2).isEqualTo(viaLista);
    }

    @Test
    @WithMockUser(roles = "SERVIDOR")
    void tempoMedioUsaAUltimaMudancaParaResolvidaEIgnoraQuemNaoTemHistorico() {
        String bairro = "B-" + UUID.randomUUID();
        User autor = usuario("autor", Role.CIDADAO);
        Denuncia comHistorico = comStatus(autor, bairro, "RESOLVIDA");
        comStatus(autor, bairro, "RESOLVIDA"); // resolvida sem histórico: conta, mas não entra na média
        jdbc.update("UPDATE denuncias SET data_criacao = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(10)), comHistorico.getId());
        historicoRepository.saveAndFlush(new HistoricoStatus(comHistorico,
                StatusDenuncia.EM_EXECUCAO, StatusDenuncia.RESOLVIDA, autor, "Concluído"));

        Indicadores i = service.painel(filtro(bairro)).geral();

        assertThat(i.resolvidas()).isEqualTo(2);
        assertThat(i.tempoMedioDias()).isBetween(9.9, 10.1);
    }

    @Test
    @WithMockUser(roles = "SERVIDOR")
    void semDadosOTempoMedioEhNuloEOPercentualEhZero() {
        Indicadores i = service.painel(filtro("inexistente-" + UUID.randomUUID())).geral();

        assertThat(i.total()).isZero();
        assertThat(i.tempoMedioDias()).isNull();
        assertThat(i.percentualResolucao()).isZero();
        assertThat(i.tempoMedioTexto()).isEqualTo("—");
    }

    @Test
    @WithMockUser(roles = "SERVIDOR")
    void agrupaPorSituacaoEPorMes() {
        String bairro = "B-" + UUID.randomUUID();
        User autor = usuario("autor", Role.CIDADAO);
        comStatus(autor, bairro, "RECEBIDA");
        comStatus(autor, bairro, "RECEBIDA");
        comStatus(autor, bairro, "RESOLVIDA");

        FiltroRelatorio porStatus = filtro(bairro);
        porStatus.setDimensao(Dimensao.STATUS);
        List<Linha> linhas = service.relatorioAgrupado(porStatus);
        assertThat(linhas).extracting(Linha::grupo).containsExactlyInAnyOrder("RECEBIDA", "RESOLVIDA");
        assertThat(linhas.stream().filter(l -> l.grupo().equals("RECEBIDA")).findFirst().orElseThrow()
                .indicadores().total()).isEqualTo(2);

        FiltroRelatorio porMes = filtro(bairro);
        porMes.setDimensao(Dimensao.MES);
        List<Linha> meses = service.relatorioAgrupado(porMes);
        assertThat(meses).hasSize(1);
        assertThat(meses.get(0).grupo()).isEqualTo(LocalDate.now().toString().substring(0, 7));
        assertThat(meses.get(0).indicadores().total()).isEqualTo(3);
    }

    @Test
    @WithMockUser(roles = "SERVIDOR")
    void filtroPorOrgaoIncluiAsUnidadesAbaixoDele() {
        String bairro = "B-" + UUID.randomUUID();
        User autor = usuario("autor", Role.CIDADAO);
        User servidor = usuario("serv", Role.SERVIDOR);
        Unidade secretaria = unidadeRepository.saveAndFlush(
                new Unidade("Sec-" + UUID.randomUUID(), TipoUnidade.SECRETARIA, null));
        Unidade departamento = unidadeRepository.saveAndFlush(
                new Unidade("Dep-" + UUID.randomUUID(), TipoUnidade.DEPARTAMENTO, secretaria));
        Unidade equipe = unidadeRepository.saveAndFlush(
                new Unidade("Eq-" + UUID.randomUUID(), TipoUnidade.EQUIPE, departamento));
        Unidade outra = unidadeRepository.saveAndFlush(
                new Unidade("Outra-" + UUID.randomUUID(), TipoUnidade.SECRETARIA, null));

        Denuncia noDepartamento = criar(autor, bairro, "No departamento");
        Denuncia naEquipe = criar(autor, bairro, "Na equipe");
        criar(autor, bairro, "Sem encaminhamento");
        encaminhamentoRepository.saveAndFlush(
                new Encaminhamento(noDepartamento.getId(), departamento, null, null, null, servidor));
        encaminhamentoRepository.saveAndFlush(
                new Encaminhamento(naEquipe.getId(), equipe, null, null, null, servidor));

        FiltroRelatorio f = filtro(bairro);
        f.setUnidadeId(secretaria.getId());
        assertThat(service.painel(f).geral().total()).isEqualTo(2);
        f.setUnidadeId(departamento.getId());
        assertThat(service.painel(f).geral().total()).isEqualTo(2);
        f.setUnidadeId(equipe.getId());
        assertThat(service.painel(f).geral().total()).isEqualTo(1);
        f.setUnidadeId(outra.getId());
        assertThat(service.painel(f).geral().total()).isZero();
    }

    @Test
    void csvDetalhadoNeutralizaFormulasEEscapaCaracteresEspeciais() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        User servidor = usuario("serv", Role.SERVIDOR);
        criar(servidor, bairro, "=1+1");
        criar(servidor, bairro, "a;\"b");

        String corpo = csv("/gestao/relatorios/ocorrencias.csv?bairro=" + bairro, servidor);

        assertThat(corpo).startsWith("\uFEFFID;Título;Categoria;");
        assertThat(corpo).contains("'=1+1").contains("\"a;\"\"b\"");
        assertThat(corpo.split("\r\n")).hasSize(3); // cabeçalho + 2 ocorrências
        assertThat(corpo).doesNotContain(servidor.getEmail()); // sem dados pessoais
    }

    @Test
    void csvAgrupadoTemOsTotaisDoFiltro() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        User servidor = usuario("serv", Role.SERVIDOR);
        comStatus(servidor, bairro, "RECEBIDA");
        comStatus(servidor, bairro, "RESOLVIDA");

        String corpo = csv("/gestao/relatorios/agrupado.csv?dimensao=STATUS&bairro=" + bairro, servidor);
        String[] linhas = corpo.split("\r\n");

        assertThat(linhas[0]).startsWith("\uFEFFSituação;Total;Pendentes;");
        assertThat(linhas).hasSize(3);
        assertThat(corpo).contains("RECEBIDA;1;1;").contains("RESOLVIDA;1;0;");
    }

    @Test
    void exportacaoFicaRegistradaNaAuditoriaSemDadosPessoais() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        User servidor = usuario("serv", Role.SERVIDOR);
        criar(servidor, bairro, "Qualquer");

        csv("/gestao/relatorios/ocorrencias.csv?bairro=" + bairro, servidor);
        csv("/gestao/relatorios/agrupado.csv?bairro=" + bairro, servidor);

        List<RegistroAuditoria> registros = auditoriaRepository.findAll().stream()
                .filter(a -> a.getAcao() == AcaoAuditoria.RELATORIO_EXPORTADO
                        && a.getResponsavel().getId().equals(servidor.getId())).toList();
        assertThat(registros).hasSize(2);
        assertThat(registros).allSatisfy(r -> assertThat(r.getDetalhes())
                .contains(bairro).doesNotContain(servidor.getEmail()));
    }

    @Test
    void paginasMostramOsDadosEIgnoramFiltrosInvalidos() throws Exception {
        User servidor = usuario("serv", Role.SERVIDOR);

        mvc().perform(get("/gestao/dashboard").with(user(servidor.getEmail()).roles("SERVIDOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("gestao-dashboard"))
                .andExpect(model().attributeExists("painel"));
        mvc().perform(get("/gestao/relatorios").with(user(servidor.getEmail()).roles("SERVIDOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("gestao-relatorios"))
                .andExpect(model().attributeExists("linhas"));

        mvc().perform(get("/gestao/dashboard").with(user(servidor.getEmail()).roles("SERVIDOR"))
                        .param("de", "2020-13-45").param("dimensao", "XYZ").param("categoriaId", "abc"))
                .andExpect(status().isOk());
    }
}