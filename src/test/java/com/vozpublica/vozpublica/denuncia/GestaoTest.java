package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
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

@SpringBootTest(properties = "app.upload.dir=target/test-uploads")
@Transactional
class GestaoTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private HistoricoStatusRepository historicoRepository;
    @Autowired private HistoricoPrioridadeRepository prioridadeRepository;
    @Autowired private EvidenciaRepository evidenciaRepository;
    @Autowired private GestaoDenunciaService gestaoService;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User usuario(String prefixo, Role papel) {
        return userRepository.saveAndFlush(new User("Teste " + prefixo,
                prefixo + "." + UUID.randomUUID() + "@teste.com", "hash", papel));
    }

    private Denuncia denuncia(User autor) {
        var categoria = categoriaRepository.findByAtivaTrueOrderByNome().get(0);
        return denunciaRepository.saveAndFlush(new Denuncia(autor, categoria,
                "Ocorrência de gestão", "Descrição de teste do problema.",
                null, "Boa Vista", "RPA 1 - Centro", null, null, Prioridade.MEDIA));
    }

    /** Descarta o cache do Hibernate para reler o que realmente está no banco. */
    private Denuncia recarregar(Long id) {
        em.flush();
        em.clear();
        return denunciaRepository.findById(id).orElseThrow();
    }

    private byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private ResultActions postStatus(User quem, Role papel, Long id, String novo, String obs,
                                     MockMultipartFile... arquivos) throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart("/gestao/denuncias/{id}/status", id);
        b.with(user(quem.getEmail()).roles(papel.name())).with(csrf());
        if (novo != null) b.param("novoStatus", novo);
        if (obs != null) b.param("observacao", obs);
        for (MockMultipartFile a : arquivos) b.file(a);
        return mvc().perform(b);
    }

    @Test
    void visitanteECidadaoNaoAcessamOPainel() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        Denuncia d = denuncia(autor);

        mvc().perform(get("/gestao/denuncias/{id}", d.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mvc().perform(get("/gestao/denuncias/{id}", d.getId())
                        .with(user(autor.getEmail()).roles("CIDADAO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cidadaoNaoAlteraStatusNemPrioridade() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        Denuncia d = denuncia(autor);

        postStatus(autor, Role.CIDADAO, d.getId(), "EM_ANALISE", "tentando")
                .andExpect(status().isForbidden());

        mvc().perform(post("/gestao/denuncias/{id}/prioridade", d.getId())
                        .with(user(autor.getEmail()).roles("CIDADAO")).with(csrf())
                        .param("prioridade", "URGENTE"))
                .andExpect(status().isForbidden());

        Denuncia relida = recarregar(d.getId());
        assertThat(relida.getStatus()).isEqualTo(StatusDenuncia.RECEBIDA);
        assertThat(relida.getPrioridadeOficial()).isNull();
    }

    @Test
    void servidorAbreOPainel() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User servidor = usuario("serv", Role.SERVIDOR);

        mvc().perform(get("/gestao/denuncias/{id}", d.getId())
                        .with(user(servidor.getEmail()).roles("SERVIDOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("gestao-denuncia"))
                .andExpect(model().attributeExists("proximos"));
    }

    @Test
    void servidorAlteraStatusEGravaHistoricoComResponsavel() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User servidor = usuario("serv", Role.SERVIDOR);

        postStatus(servidor, Role.SERVIDOR, d.getId(), "EM_ANALISE", "  Vamos analisar.  ")
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("mensagem"));

        assertThat(recarregar(d.getId()).getStatus()).isEqualTo(StatusDenuncia.EM_ANALISE);

        List<HistoricoStatus> historico = historicoRepository.findByDenunciaIdOrderByDataAlteracaoAsc(d.getId());
        assertThat(historico).hasSize(1);
        assertThat(historico.get(0).getStatusAnterior()).isEqualTo(StatusDenuncia.RECEBIDA);
        assertThat(historico.get(0).getStatusNovo()).isEqualTo(StatusDenuncia.EM_ANALISE);
        assertThat(historico.get(0).getObservacao()).isEqualTo("Vamos analisar.");
        assertThat(historico.get(0).getResponsavel().getEmail()).isEqualTo(servidor.getEmail());
    }

    @Test
    void transicaoForaDaTabelaEhRejeitada() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User servidor = usuario("serv", Role.SERVIDOR);

        postStatus(servidor, Role.SERVIDOR, d.getId(), "RESOLVIDA", "Pulando etapas",
                new MockMultipartFile("evidencias", "a.png", "image/png", png()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("erro"));

        assertThat(recarregar(d.getId()).getStatus()).isEqualTo(StatusDenuncia.RECEBIDA);
        assertThat(historicoRepository.findByDenunciaIdOrderByDataAlteracaoAsc(d.getId())).isEmpty();
    }

    @Test
    void resolverExigeObservacao() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User servidor = usuario("serv", Role.SERVIDOR);
        jdbc.update("UPDATE denuncias SET status = 'EM_EXECUCAO' WHERE id = ?", d.getId());
        recarregar(d.getId());

        postStatus(servidor, Role.SERVIDOR, d.getId(), "RESOLVIDA", "   ",
                new MockMultipartFile("evidencias", "a.png", "image/png", png()))
                .andExpect(flash().attributeExists("erro"));

        assertThat(recarregar(d.getId()).getStatus()).isEqualTo(StatusDenuncia.EM_EXECUCAO);
    }

    @Test
    void resolverExigeEvidencia() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User servidor = usuario("serv", Role.SERVIDOR);
        jdbc.update("UPDATE denuncias SET status = 'EM_EXECUCAO' WHERE id = ?", d.getId());
        recarregar(d.getId());

        postStatus(servidor, Role.SERVIDOR, d.getId(), "RESOLVIDA", "Serviço concluído.")
                .andExpect(flash().attributeExists("erro"));

        assertThat(recarregar(d.getId()).getStatus()).isEqualTo(StatusDenuncia.EM_EXECUCAO);
    }

    @Test
    void resolverComObservacaoEEvidenciaFuncionaEAEvidenciaEPublica() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User servidor = usuario("serv", Role.SERVIDOR);
        jdbc.update("UPDATE denuncias SET status = 'EM_EXECUCAO' WHERE id = ?", d.getId());
        recarregar(d.getId());

        postStatus(servidor, Role.SERVIDOR, d.getId(), "RESOLVIDA", "Buraco tapado.",
                new MockMultipartFile("evidencias", "a.png", "image/png", png()))
                .andExpect(flash().attributeExists("mensagem"));

        assertThat(recarregar(d.getId()).getStatus()).isEqualTo(StatusDenuncia.RESOLVIDA);

        HistoricoStatus h = historicoRepository.findByDenunciaIdOrderByDataAlteracaoAsc(d.getId()).get(0);
        List<Evidencia> provas = evidenciaRepository.findByHistoricoIdInOrderByIdAsc(List.of(h.getId()));
        assertThat(provas).hasSize(1);

        // qualquer visitante vê a evidência
        mvc().perform(get("/evidencias/{id}", provas.get(0).getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void servidorDefineEMudaAPrioridadeOficialERepeticaoEhRejeitada() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User servidor = usuario("serv", Role.SERVIDOR);

        mvc().perform(post("/gestao/denuncias/{id}/prioridade", d.getId())
                        .with(user(servidor.getEmail()).roles("SERVIDOR")).with(csrf())
                        .param("prioridade", "URGENTE").param("observacao", "Risco à segurança"))
                .andExpect(flash().attributeExists("mensagem"));

        assertThat(recarregar(d.getId()).getPrioridadeOficial()).isEqualTo(Prioridade.URGENTE);
        assertThat(recarregar(d.getId()).getPrioridadeSugerida()).isEqualTo(Prioridade.MEDIA); // intocada

        mvc().perform(post("/gestao/denuncias/{id}/prioridade", d.getId())
                        .with(user(servidor.getEmail()).roles("SERVIDOR")).with(csrf())
                        .param("prioridade", "URGENTE"))
                .andExpect(flash().attributeExists("erro"));

        var historico = prioridadeRepository.findByDenunciaIdOrderByDataAlteracaoAsc(d.getId());
        assertThat(historico).hasSize(1);
        assertThat(historico.get(0).getPrioridadeAnterior()).isNull();
        assertThat(historico.get(0).getPrioridadeNova()).isEqualTo(Prioridade.URGENTE);
    }

    @Test
    void historicoNaoPodeSerAlteradoNemPorSql() {
        User servidor = usuario("serv", Role.SERVIDOR);
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        historicoRepository.saveAndFlush(
                new HistoricoStatus(d, null, StatusDenuncia.RECEBIDA, servidor, "original"));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE historico_status SET observacao = 'alterada' WHERE denuncia_id = ?", d.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @WithMockUser(roles = "CIDADAO")
    void servicoRecusaChamadaDeCidadaoMesmoSemPassarPeloControlador() {
        assertThatThrownBy(() -> gestaoService.definirPrioridadeOficial(
                1L, Prioridade.ALTA, null, "qualquer@teste.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void detalheSoOferecePainelParaAEquipe() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User servidor = usuario("serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor);

        mvc().perform(get("/denuncias/{id}", d.getId()).with(user(autor.getEmail()).roles("CIDADAO")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("podeGerir", false));

        mvc().perform(get("/denuncias/{id}", d.getId()).with(user(servidor.getEmail()).roles("SERVIDOR")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("podeGerir", true));
    }
}