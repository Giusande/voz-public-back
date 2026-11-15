package com.vozpublica.vozpublica.moderacao;

import com.vozpublica.vozpublica.auditoria.AcaoAuditoria;
import com.vozpublica.vozpublica.auditoria.AuditoriaRepository;
import com.vozpublica.vozpublica.auditoria.RegistroAuditoria;
import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.comentario.Comentario;
import com.vozpublica.vozpublica.comentario.ComentarioRepository;
import com.vozpublica.vozpublica.denuncia.*;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ModeracaoTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ComentarioRepository comentarioRepository;
    @Autowired private SinalizacaoRepository sinalizacaoRepository;
    @Autowired private AuditoriaRepository auditoriaRepository;
    @Autowired private FotoRepository fotoRepository;
    @Autowired private EvidenciaRepository evidenciaRepository;
    @Autowired private HistoricoStatusRepository historicoRepository;
    @Autowired private DenunciaConsultaService consulta;
    @Autowired private ModeracaoService moderacaoService;
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

    private Comentario comentario(Denuncia d, User autor, String texto) {
        return comentarioRepository.saveAndFlush(new Comentario(d.getId(), autor, texto));
    }

    private ResultActions postar(User quem, Role papel, String caminho, String... pares) throws Exception {
        var b = post(caminho).with(user(quem.getEmail()).roles(papel.name())).with(csrf());
        for (int i = 0; i < pares.length; i += 2) {
            b.param(pares[i], pares[i + 1]);
        }
        return mvc().perform(b);
    }

    private void limpar() {
        em.flush();
        em.clear();
    }

    private Denuncia reler(Long id) {
        limpar();
        return denunciaRepository.findById(id).orElseThrow();
    }

    private List<Sinalizacao> sinalizacoes(TipoConteudo tipo, Long alvoId) {
        limpar();
        return sinalizacaoRepository.findAll().stream()
                .filter(s -> s.getTipo() == tipo && s.getAlvoId().equals(alvoId)).toList();
    }

    private List<RegistroAuditoria> auditoria(AcaoAuditoria acao, String entidade, Long id) {
        limpar();
        return auditoriaRepository.findAll().stream()
                .filter(a -> a.getAcao() == acao && a.getEntidade().equals(entidade)
                        && Objects.equals(a.getEntidadeId(), id)).toList();
    }

    private String ocultarDenuncia(Long id) {
        return "/admin/moderacao/conteudo/DENUNCIA/" + id + "/ocultar";
    }

    private String html(String url, User quem, Role papel) throws Exception {
        var req = get(url);
        if (quem != null) req.with(user(quem.getEmail()).roles(papel.name()));
        return mvc().perform(req).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void soAdministradorAcessaAFilaEAsAcoes() throws Exception {
        User cidadao = usuario("cidadao", Role.CIDADAO);
        User servidor = usuario("serv", Role.SERVIDOR);
        Denuncia d = denuncia(cidadao, "B-" + UUID.randomUUID());

        mvc().perform(get("/admin/moderacao"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc().perform(get("/admin/moderacao").with(user(cidadao.getEmail()).roles("CIDADAO")))
                .andExpect(status().isForbidden());
        mvc().perform(get("/admin/moderacao").with(user(servidor.getEmail()).roles("SERVIDOR")))
                .andExpect(status().isForbidden());

        postar(servidor, Role.SERVIDOR, ocultarDenuncia(d.getId()), "justificativa", "x")
                .andExpect(status().isForbidden());
        postar(cidadao, Role.CIDADAO, ocultarDenuncia(d.getId()), "justificativa", "x")
                .andExpect(status().isForbidden());

        assertThat(reler(d.getId()).isOculta()).isFalse();
    }

    @Test
    void cidadaoSinalizaPublicacaoERepeticaoEhRejeitada() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User denunciante = usuario("denunciante", Role.CIDADAO);
        Denuncia d = denuncia(autor, "B-" + UUID.randomUUID());
        String url = "/denuncias/" + d.getId() + "/sinalizar";

        postar(denunciante, Role.CIDADAO, url, "motivo", "SPAM", "detalhe", "  propaganda  ")
                .andExpect(flash().attributeExists("mensagem"));

        List<Sinalizacao> lista = sinalizacoes(TipoConteudo.DENUNCIA, d.getId());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).getStatus()).isEqualTo(StatusSinalizacao.ABERTA);
        assertThat(lista.get(0).getMotivo()).isEqualTo(MotivoSinalizacao.SPAM);
        assertThat(lista.get(0).getDetalhe()).isEqualTo("propaganda");
        assertThat(lista.get(0).getUsuarioId()).isEqualTo(denunciante.getId());

        postar(denunciante, Role.CIDADAO, url, "motivo", "OFENSIVO")
                .andExpect(flash().attributeExists("erro"));
        assertThat(sinalizacoes(TipoConteudo.DENUNCIA, d.getId())).hasSize(1);
    }

    @Test
    void visitanteNaoSinaliza() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO), "B-" + UUID.randomUUID());

        mvc().perform(post("/denuncias/{id}/sinalizar", d.getId()).with(csrf()).param("motivo", "SPAM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        assertThat(sinalizacoes(TipoConteudo.DENUNCIA, d.getId())).isEmpty();
    }

    @Test
    void sinalizacaoExigeMotivo() throws Exception {
        User denunciante = usuario("denunciante", Role.CIDADAO);
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO), "B-" + UUID.randomUUID());

        postar(denunciante, Role.CIDADAO, "/denuncias/" + d.getId() + "/sinalizar", "detalhe", "sem motivo")
                .andExpect(flash().attributeExists("erro"));

        assertThat(sinalizacoes(TipoConteudo.DENUNCIA, d.getId())).isEmpty();
    }

    @Test
    void sinalizarComentarioValidaOVinculoComAPublicacao() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User denunciante = usuario("denunciante", Role.CIDADAO);
        Denuncia d1 = denuncia(autor, "B-" + UUID.randomUUID());
        Denuncia d2 = denuncia(autor, "B-" + UUID.randomUUID());
        Comentario c = comentario(d1, autor, "Um comentário qualquer");

        postar(denunciante, Role.CIDADAO,
                "/denuncias/" + d2.getId() + "/comentarios/" + c.getId() + "/sinalizar", "motivo", "SPAM")
                .andExpect(flash().attributeExists("erro"));
        assertThat(sinalizacoes(TipoConteudo.COMENTARIO, c.getId())).isEmpty();

        postar(denunciante, Role.CIDADAO,
                "/denuncias/" + d1.getId() + "/comentarios/" + c.getId() + "/sinalizar", "motivo", "SPAM")
                .andExpect(flash().attributeExists("mensagem"));
        List<Sinalizacao> lista = sinalizacoes(TipoConteudo.COMENTARIO, c.getId());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).getDenunciaId()).isEqualTo(d1.getId());
    }

    @Test
    void publicacaoOcultaSomeParaTodosMenosParaAdministradores() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        User admin = usuario("admin", Role.ADMINISTRADOR);
        User autor = usuario("autor", Role.CIDADAO);
        User outro = usuario("outro", Role.CIDADAO);
        Denuncia d = denuncia(autor, bairro);
        FiltroDenuncia filtro = new FiltroDenuncia();
        filtro.setBairro(bairro);

        limpar();
        assertThat(consulta.buscar(filtro, 0).page().getContent()).hasSize(1);

        postar(admin, Role.ADMINISTRADOR, ocultarDenuncia(d.getId()),
                "justificativa", "Conteúdo ofensivo", "denunciaId", String.valueOf(d.getId()))
                .andExpect(flash().attributeExists("mensagem"))
                .andExpect(redirectedUrl("/denuncias/" + d.getId()));

        limpar();
        assertThat(consulta.buscar(filtro, 0).page().getContent()).isEmpty();

        mvc().perform(get("/denuncias/{id}", d.getId())).andExpect(status().isNotFound());
        mvc().perform(get("/denuncias/{id}", d.getId()).with(user(outro.getEmail()).roles("CIDADAO")))
                .andExpect(status().isNotFound());
        mvc().perform(get("/denuncias/{id}", d.getId()).with(user(admin.getEmail()).roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
    }

    @Test
    void ocultarSemJustificativaNaoMudaNada() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO), "B-" + UUID.randomUUID());
        long antes = auditoriaRepository.count();

        postar(admin, Role.ADMINISTRADOR, ocultarDenuncia(d.getId()), "justificativa", "   ")
                .andExpect(flash().attributeExists("erro"));

        assertThat(reler(d.getId()).isOculta()).isFalse();
        assertThat(auditoriaRepository.count()).isEqualTo(antes);
    }

    @Test
    void publicacaoOcultaNaoRecebeInteracoes() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        User autor = usuario("autor", Role.CIDADAO);
        User cidadao = usuario("cidadao", Role.CIDADAO);
        Denuncia d = denuncia(autor, "B-" + UUID.randomUUID());
        postar(admin, Role.ADMINISTRADOR, ocultarDenuncia(d.getId()), "justificativa", "teste")
                .andExpect(flash().attributeExists("mensagem"));

        String base = "/denuncias/" + d.getId();
        postar(cidadao, Role.CIDADAO, base + "/curtir").andExpect(status().isNotFound());
        postar(cidadao, Role.CIDADAO, base + "/incentivar").andExpect(status().isNotFound());
        postar(cidadao, Role.CIDADAO, base + "/comentarios", "conteudo", "Olá")
                .andExpect(status().isNotFound());
        postar(cidadao, Role.CIDADAO, base + "/sinalizar", "motivo", "SPAM")
                .andExpect(status().isNotFound());
    }

    @Test
    void ocultarEncerraAsSinalizacoesAbertasEAudita() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        User autor = usuario("autor", Role.CIDADAO);
        User r1 = usuario("rep1", Role.CIDADAO);
        User r2 = usuario("rep2", Role.CIDADAO);
        Denuncia d = denuncia(autor, "B-" + UUID.randomUUID());
        String url = "/denuncias/" + d.getId() + "/sinalizar";
        postar(r1, Role.CIDADAO, url, "motivo", "OFENSIVO");
        postar(r2, Role.CIDADAO, url, "motivo", "SPAM");
        assertThat(sinalizacoes(TipoConteudo.DENUNCIA, d.getId()))
                .hasSize(2).allMatch(s -> s.getStatus() == StatusSinalizacao.ABERTA);

        postar(admin, Role.ADMINISTRADOR, ocultarDenuncia(d.getId()), "justificativa", "Conteúdo ofensivo")
                .andExpect(flash().attributeExists("mensagem"));

        assertThat(reler(d.getId()).isOculta()).isTrue();
        assertThat(sinalizacoes(TipoConteudo.DENUNCIA, d.getId())).hasSize(2)
                .allMatch(s -> s.getStatus() == StatusSinalizacao.PROCEDENTE
                        && admin.getId().equals(s.getAnalisadoPorId())
                        && "Conteúdo ofensivo".equals(s.getJustificativaAnalise()));

        List<RegistroAuditoria> aud = auditoria(AcaoAuditoria.CONTEUDO_OCULTADO, "DENUNCIA", d.getId());
        assertThat(aud).hasSize(1);
        assertThat(aud.get(0).getJustificativa()).isEqualTo("Conteúdo ofensivo");
        assertThat(aud.get(0).getResponsavel().getEmail()).isEqualTo(admin.getEmail());
        assertThat(aud.get(0).getDetalhes()).doesNotContain(r1.getEmail()).doesNotContain(r2.getEmail());
    }

    @Test
    void restaurarFazAPublicacaoVoltarEFicaAuditado() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        User admin = usuario("admin", Role.ADMINISTRADOR);
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO), bairro);
        String restaurar = "/admin/moderacao/conteudo/DENUNCIA/" + d.getId() + "/restaurar";

        postar(admin, Role.ADMINISTRADOR, restaurar, "justificativa", "x")
                .andExpect(flash().attributeExists("erro")); // não está oculta

        postar(admin, Role.ADMINISTRADOR, ocultarDenuncia(d.getId()), "justificativa", "Engano");
        mvc().perform(get("/denuncias/{id}", d.getId())).andExpect(status().isNotFound());

        postar(admin, Role.ADMINISTRADOR, restaurar, "justificativa", "Recurso aceito")
                .andExpect(flash().attributeExists("mensagem"));

        mvc().perform(get("/denuncias/{id}", d.getId())).andExpect(status().isOk());
        List<RegistroAuditoria> aud = auditoria(AcaoAuditoria.CONTEUDO_RESTAURADO, "DENUNCIA", d.getId());
        assertThat(aud).hasSize(1);
        assertThat(aud.get(0).getJustificativa()).isEqualTo("Recurso aceito");
    }

    @Test
    void ocultarDuasVezesEhRejeitado() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO), "B-" + UUID.randomUUID());

        postar(admin, Role.ADMINISTRADOR, ocultarDenuncia(d.getId()), "justificativa", "primeira")
                .andExpect(flash().attributeExists("mensagem"));
        postar(admin, Role.ADMINISTRADOR, ocultarDenuncia(d.getId()), "justificativa", "segunda")
                .andExpect(flash().attributeExists("erro"));

        assertThat(auditoria(AcaoAuditoria.CONTEUDO_OCULTADO, "DENUNCIA", d.getId())).hasSize(1);
    }

    @Test
    void comentarioOcultoSomeParaVisitantesMasNaoParaAdministradores() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        String marca = "texto-ofensivo-" + UUID.randomUUID();
        User admin = usuario("admin", Role.ADMINISTRADOR);
        User autor = usuario("autor", Role.CIDADAO);
        Denuncia d = denuncia(autor, bairro);
        Comentario c = comentario(d, autor, marca);
        FiltroDenuncia filtro = new FiltroDenuncia();
        filtro.setBairro(bairro);

        limpar();
        assertThat(consulta.buscar(filtro, 0).contadores().get(d.getId()).comentarios()).isEqualTo(1);
        assertThat(html("/denuncias/" + d.getId(), null, null)).contains(marca);

        postar(admin, Role.ADMINISTRADOR, "/admin/moderacao/conteudo/COMENTARIO/" + c.getId() + "/ocultar",
                "justificativa", "Ofensivo", "denunciaId", String.valueOf(d.getId()))
                .andExpect(flash().attributeExists("mensagem"));

        limpar();
        assertThat(comentarioRepository.findById(c.getId()).orElseThrow().isOculto()).isTrue();
        assertThat(consulta.buscar(filtro, 0).contadores().get(d.getId()).comentarios()).isZero();
        assertThat(html("/denuncias/" + d.getId(), null, null)).doesNotContain(marca);

        String paraAdmin = html("/denuncias/" + d.getId(), admin, Role.ADMINISTRADOR);
        assertThat(paraAdmin).contains(marca).contains("Oculto pela moderação");

        postar(admin, Role.ADMINISTRADOR, "/admin/moderacao/conteudo/COMENTARIO/" + c.getId() + "/restaurar",
                "justificativa", "Recurso aceito", "denunciaId", String.valueOf(d.getId()))
                .andExpect(flash().attributeExists("mensagem"));

        assertThat(html("/denuncias/" + d.getId(), null, null)).contains(marca);
        assertThat(auditoria(AcaoAuditoria.CONTEUDO_OCULTADO, "COMENTARIO", c.getId())).hasSize(1);
        assertThat(auditoria(AcaoAuditoria.CONTEUDO_RESTAURADO, "COMENTARIO", c.getId())).hasSize(1);
    }

    @Test
    void descartarSinalizacaoMantemOConteudoNoAr() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        User denunciante = usuario("denunciante", Role.CIDADAO);
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO), "B-" + UUID.randomUUID());
        postar(denunciante, Role.CIDADAO, "/denuncias/" + d.getId() + "/sinalizar", "motivo", "FALSO");
        Long sid = sinalizacoes(TipoConteudo.DENUNCIA, d.getId()).get(0).getId();
        String descartar = "/admin/moderacao/sinalizacoes/" + sid + "/descartar";

        postar(admin, Role.ADMINISTRADOR, descartar, "justificativa", "Sem fundamento")
                .andExpect(flash().attributeExists("mensagem"));

        Sinalizacao s = sinalizacoes(TipoConteudo.DENUNCIA, d.getId()).get(0);
        assertThat(s.getStatus()).isEqualTo(StatusSinalizacao.IMPROCEDENTE);
        assertThat(s.getAnalisadoPorId()).isEqualTo(admin.getId());
        assertThat(reler(d.getId()).isOculta()).isFalse();
        assertThat(auditoria(AcaoAuditoria.SINALIZACAO_DESCARTADA, "SINALIZACAO", sid)).hasSize(1);

        postar(admin, Role.ADMINISTRADOR, descartar, "justificativa", "De novo")
                .andExpect(flash().attributeExists("erro"));
    }

    @Test
    void ocultarPelaSinalizacaoOcultaOConteudoEEncerraARegistro() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        User autor = usuario("autor", Role.CIDADAO);
        User denunciante = usuario("denunciante", Role.CIDADAO);
        Denuncia d = denuncia(autor, "B-" + UUID.randomUUID());
        Comentario c = comentario(d, autor, "comentário problemático");
        postar(denunciante, Role.CIDADAO,
                "/denuncias/" + d.getId() + "/comentarios/" + c.getId() + "/sinalizar", "motivo", "OFENSIVO");
        Long sid = sinalizacoes(TipoConteudo.COMENTARIO, c.getId()).get(0).getId();

        postar(admin, Role.ADMINISTRADOR, "/admin/moderacao/sinalizacoes/" + sid + "/ocultar",
                "justificativa", "Ofensivo").andExpect(flash().attributeExists("mensagem"));

        limpar();
        assertThat(comentarioRepository.findById(c.getId()).orElseThrow().isOculto()).isTrue();
        assertThat(sinalizacoes(TipoConteudo.COMENTARIO, c.getId()).get(0).getStatus())
                .isEqualTo(StatusSinalizacao.PROCEDENTE);
    }

    @Test
    void filaMostraOConteudoMasNaoRevelaQuemSinalizou() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        User autor = usuario("autor", Role.CIDADAO);
        User denunciante = usuario("denunciante", Role.CIDADAO);
        Denuncia d = denuncia(autor, "B-" + UUID.randomUUID());
        String marca = "trecho-" + UUID.randomUUID();
        Comentario c = comentario(d, autor, marca);
        postar(denunciante, Role.CIDADAO,
                "/denuncias/" + d.getId() + "/comentarios/" + c.getId() + "/sinalizar", "motivo", "SPAM");
        limpar();

        // a nova sinalização é a mais recente, então fica na última página
        Page<?> primeira = (Page<?>) mvc().perform(get("/admin/moderacao")
                        .with(user(admin.getEmail()).roles("ADMINISTRADOR")))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel().get("page");
        int ultima = Math.max(primeira.getTotalPages() - 1, 0);

        String pagina = mvc().perform(get("/admin/moderacao").param("pagina", String.valueOf(ultima))
                        .with(user(admin.getEmail()).roles("ADMINISTRADOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(pagina).contains(marca);
        assertThat(pagina).doesNotContain(denunciante.getEmail()).doesNotContain(denunciante.getName());
    }

    @Test
    void existeLimiteDiarioDeSinalizacoesPorUsuario() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User denunciante = usuario("denunciante", Role.CIDADAO);
        Denuncia d = denuncia(autor, "B-" + UUID.randomUUID());

        for (int i = 0; i < 20; i++) {
            sinalizacaoRepository.inserir("COMENTARIO", 900_000L + i, d.getId(),
                    denunciante.getId(), "SPAM", "");
        }
        limpar();

        postar(denunciante, Role.CIDADAO, "/denuncias/" + d.getId() + "/sinalizar", "motivo", "SPAM")
                .andExpect(flash().attributeExists("erro"));

        assertThat(sinalizacaoRepository.countByUsuarioIdAndDataCriacaoAfter(
                denunciante.getId(), LocalDateTime.now().minusDays(1))).isEqualTo(20);
    }

    @Test
    @WithMockUser(roles = "SERVIDOR")
    void servicoRecusaChamadaDeNaoAdministradorMesmoSemPassarPeloControlador() {
        assertThatThrownBy(() -> moderacaoService.ocultar(TipoConteudo.DENUNCIA, 1L, "x", "a@teste.com"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> moderacaoService.fila(0))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void fotosEEvidenciasDePublicacaoOcultaDeixamDeSerVisiveis() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        User autor = usuario("autor", Role.CIDADAO);
        Denuncia d = denuncia(autor, "B-" + UUID.randomUUID());
        Foto foto = fotoRepository.saveAndFlush(new Foto(d, "arquivo.png"));
        HistoricoStatus h = historicoRepository.saveAndFlush(
                new HistoricoStatus(d, null, StatusDenuncia.RECEBIDA, autor, "início"));
        Evidencia e = evidenciaRepository.saveAndFlush(new Evidencia(h.getId(), "prova.png"));
        limpar();

        assertThat(fotoRepository.existsByIdAndDenunciaOcultaFalse(foto.getId())).isTrue();
        assertThat(evidenciaRepository.contarVisiveis(e.getId())).isEqualTo(1);

        postar(admin, Role.ADMINISTRADOR, ocultarDenuncia(d.getId()), "justificativa", "teste")
                .andExpect(flash().attributeExists("mensagem"));
        limpar();

        assertThat(fotoRepository.existsByIdAndDenunciaOcultaFalse(foto.getId())).isFalse();
        assertThat(evidenciaRepository.contarVisiveis(e.getId())).isZero();
    }
}