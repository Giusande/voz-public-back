package com.vozpublica.vozpublica.solicitacao;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.denuncia.Prioridade;
import com.vozpublica.vozpublica.notificacao.Notificacao;
import com.vozpublica.vozpublica.notificacao.NotificacaoRepository;
import com.vozpublica.vozpublica.notificacao.TipoNotificacao;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
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
class SolicitacaoInfoTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private SolicitacaoInfoRepository solicitacaoRepository;
    @Autowired private NotificacaoRepository notificacaoRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User usuario(String nome, Role papel) {
        return userRepository.saveAndFlush(new User(nome + " " + UUID.randomUUID(),
                "sol." + UUID.randomUUID() + "@teste.com", "hash", papel));
    }

    private Denuncia denuncia(User autor) {
        return denunciaRepository.saveAndFlush(new Denuncia(autor,
                categoriaRepository.findByAtivaTrueOrderByNome().get(0), "Ocorrência " + UUID.randomUUID(),
                "Descrição de teste do problema.", null, "B-" + UUID.randomUUID(), "RPA 1 - Centro",
                null, null, Prioridade.MEDIA));
    }

    private void limpar() {
        em.flush();
        em.clear();
    }

    private ResultActions postar(User quem, String caminho, String... pares) throws Exception {
        var b = post(caminho).with(user(quem.getEmail()).roles(quem.getRole().name())).with(csrf());
        for (int i = 0; i < pares.length; i += 2) b.param(pares[i], pares[i + 1]);
        return mvc().perform(b);
    }

    private ResultActions pedir(User serv, Long id, String mensagem) throws Exception {
        return postar(serv, "/gestao/denuncias/" + id + "/solicitar-info", "mensagem", mensagem);
    }

    private ResultActions responder(User quem, Long id, String resposta) throws Exception {
        return postar(quem, "/minhas-denuncias/" + id + "/responder", "resposta", resposta);
    }

    private List<SolicitacaoInfo> doPedido(Long denunciaId) {
        limpar();
        return solicitacaoRepository.findByDenunciaIdOrderByIdDesc(denunciaId);
    }

    private List<Notificacao> de(User u) {
        limpar();
        return notificacaoRepository.findAll().stream().filter(n -> n.getUsuarioId().equals(u.getId())).toList();
    }

    @Test
    void soAEquipePedeInformacoes() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        Denuncia d = denuncia(autor);

        mvc().perform(post("/gestao/denuncias/{id}/solicitar-info", d.getId()).with(csrf())
                        .param("mensagem", "x"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        pedir(autor, d.getId(), "x").andExpect(status().isForbidden());

        assertThat(doPedido(d.getId())).isEmpty();
    }

    @Test
    void pedidoFicaRegistradoENotificaOAutor() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor);

        pedir(serv, d.getId(), "  Qual é o número do poste?  ").andExpect(flash().attributeExists("mensagem"));

        List<SolicitacaoInfo> lista = doPedido(d.getId());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).getMensagem()).isEqualTo("Qual é o número do poste?");
        assertThat(lista.get(0).getSolicitante().getEmail()).isEqualTo(serv.getEmail());
        assertThat(lista.get(0).isAberta()).isTrue();
        assertThat(de(autor)).extracting(Notificacao::getTipo).containsExactly(TipoNotificacao.INFORMACAO);
    }

    @Test
    void naoHaSegundoPedidoEnquantoOPrimeiroEstaAberto() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor);

        pedir(serv, d.getId(), "Primeiro").andExpect(flash().attributeExists("mensagem"));
        pedir(serv, d.getId(), "Segundo").andExpect(flash().attributeExists("erro"));

        assertThat(doPedido(d.getId())).hasSize(1);
    }

    @Test
    void mensagemObrigatoriaEComLimite() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor);

        pedir(serv, d.getId(), "   ").andExpect(flash().attributeExists("erro"));
        pedir(serv, d.getId(), "a".repeat(1001)).andExpect(flash().attributeExists("erro"));

        assertThat(doPedido(d.getId())).isEmpty();
    }

    @Test
    void ocorrenciaOcultaOuAgrupadaNaoRecebePedido() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia principal = denuncia(autor);
        Denuncia oculta = denuncia(autor);
        Denuncia filha = denuncia(autor);
        jdbc.update("UPDATE denuncias SET oculta = TRUE WHERE id = ?", oculta.getId());
        jdbc.update("UPDATE denuncias SET duplicada_de_id = ? WHERE id = ?", principal.getId(), filha.getId());
        limpar();

        pedir(serv, oculta.getId(), "x").andExpect(flash().attributeExists("erro"));
        pedir(serv, filha.getId(), "x").andExpect(flash().attributeExists("erro"));

        assertThat(doPedido(oculta.getId())).isEmpty();
        assertThat(doPedido(filha.getId())).isEmpty();
    }

    @Test
    void autorRespondeEAEquipeEhNotificadaEPodePedirDeNovo() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor);
        pedir(serv, d.getId(), "Qual o número?");

        responder(autor, d.getId(), "  É o 1234  ").andExpect(flash().attributeExists("mensagem"));

        SolicitacaoInfo s = doPedido(d.getId()).get(0);
        assertThat(s.isAberta()).isFalse();
        assertThat(s.getResposta()).isEqualTo("É o 1234");
        assertThat(s.getDataResposta()).isNotNull();
        assertThat(de(serv)).extracting(Notificacao::getTipo)
                .containsExactly(TipoNotificacao.RESPOSTA_INFORMACAO);

        pedir(serv, d.getId(), "Outra dúvida").andExpect(flash().attributeExists("mensagem"));
        assertThat(doPedido(d.getId())).hasSize(2);
    }

    @Test
    void outraPessoaNaoRespondeEnaoHaRespostaSemPedido() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User intruso = usuario("Intruso", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor);

        responder(autor, d.getId(), "Sem pedido").andExpect(flash().attributeExists("erro"));

        pedir(serv, d.getId(), "Pergunta");
        responder(intruso, d.getId(), "Eu respondo").andExpect(flash().attributeExists("erro"));
        responder(autor, d.getId(), "   ").andExpect(flash().attributeExists("erro"));

        assertThat(doPedido(d.getId()).get(0).isAberta()).isTrue();
    }

    @Test
    void naoSeRespondeDuasVezes() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor);
        pedir(serv, d.getId(), "Pergunta");

        responder(autor, d.getId(), "Primeira").andExpect(flash().attributeExists("mensagem"));
        responder(autor, d.getId(), "Segunda").andExpect(flash().attributeExists("erro"));

        assertThat(doPedido(d.getId()).get(0).getResposta()).isEqualTo("Primeira");
    }

    @Test
    void pedidoERespostaSoAparecemParaOAutorEParaAEquipe() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor);
        String pergunta = "pergunta-" + UUID.randomUUID();
        pedir(serv, d.getId(), pergunta);
        limpar();

        String publica = mvc().perform(get("/denuncias/{id}", d.getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String minhas = mvc().perform(get("/minhas-denuncias").with(user(autor.getEmail()).roles("CIDADAO")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String painel = mvc().perform(get("/gestao/denuncias/{id}", d.getId())
                        .with(user(serv.getEmail()).roles("SERVIDOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(publica).doesNotContain(pergunta);
        assertThat(minhas).contains(pergunta).contains("A equipe pediu mais informações");
        assertThat(painel).contains(pergunta).contains("Aguardando resposta");
    }

    @Test
    void bancoNaoPermiteEditarOPedidoNemResponderDuasVezes() {
        User autor = usuario("Autor", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor);
        SolicitacaoInfo s = solicitacaoRepository.saveAndFlush(new SolicitacaoInfo(d.getId(), serv, "original"));

        jdbc.update("UPDATE solicitacoes_info SET resposta = 'a', data_resposta = NOW() WHERE id = ?", s.getId());

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE solicitacoes_info SET resposta = 'b' WHERE id = ?", s.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void bancoBarraSegundoPedidoAbertoPorSql() {
        User autor = usuario("Autor", Role.CIDADAO);
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(autor);
        solicitacaoRepository.saveAndFlush(new SolicitacaoInfo(d.getId(), serv, "primeiro"));

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO solicitacoes_info (denuncia_id, solicitante_id, mensagem) VALUES (?, ?, 'segundo')",
                d.getId(), serv.getId()))
                .isInstanceOf(DataAccessException.class);
    }
}