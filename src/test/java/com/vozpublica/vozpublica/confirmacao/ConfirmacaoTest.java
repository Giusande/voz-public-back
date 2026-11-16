package com.vozpublica.vozpublica.confirmacao;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.denuncia.Prioridade;
import com.vozpublica.vozpublica.denuncia.StatusDenuncia;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
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
class ConfirmacaoTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ConfirmacaoRepository confirmacaoRepository;
    @Autowired private ConfirmacaoService confirmacaoService;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User usuario(String nome, Role papel) {
        return userRepository.saveAndFlush(new User(nome + " " + UUID.randomUUID(),
                "conf." + UUID.randomUUID() + "@teste.com", "hash", papel));
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

    private ResultActions votar(User quem, Long id, String tipo) throws Exception {
        var b = post("/denuncias/" + id + "/confirmar")
                .with(user(quem.getEmail()).roles(quem.getRole().name())).with(csrf());
        if (tipo != null) b.param("tipo", tipo);
        return mvc().perform(b);
    }

    /** Resolve a ocorrência por SQL, com a última resolução em "quando". */
    private void resolver(Denuncia d, User responsavel, LocalDateTime quando) {
        jdbc.update("UPDATE denuncias SET status = 'RESOLVIDA' WHERE id = ?", d.getId());
        jdbc.update("INSERT INTO historico_status (denuncia_id, status_anterior, status_novo, responsavel_id, "
                        + "observacao, data_alteracao) VALUES (?, 'EM_EXECUCAO', 'RESOLVIDA', ?, 'ok', ?)",
                d.getId(), responsavel.getId(), Timestamp.valueOf(quando));
    }

    private void varios(Denuncia d, String tipo, int quantos) throws Exception {
        for (int i = 0; i < quantos; i++) {
            votar(usuario("Votante", Role.CIDADAO), d.getId(), tipo).andExpect(status().is3xxRedirection());
        }
    }

    @SuppressWarnings("unchecked")
    private List<DenunciaDivergente> lista(User serv) throws Exception {
        limpar();
        return (List<DenunciaDivergente>) mvc().perform(get("/gestao/reavaliacao")
                        .with(user(serv.getEmail()).roles("SERVIDOR")))
                .andExpect(status().isOk()).andExpect(view().name("gestao-reavaliacao"))
                .andReturn().getModelAndView().getModel().get("itens");
    }

    private boolean listada(User serv, Denuncia d) throws Exception {
        return lista(serv).stream().anyMatch(i -> i.getId().equals(d.getId()));
    }

    @Test
    void visitanteNaoVotaEPrecisaDeToken() throws Exception {
        Denuncia d = denuncia(usuario("Autor", Role.CIDADAO));
        User u = usuario("U", Role.CIDADAO);

        mvc().perform(post("/denuncias/{id}/confirmar", d.getId()).with(csrf()).param("tipo", "TAMBEM_EXISTE"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc().perform(post("/denuncias/{id}/confirmar", d.getId())
                        .with(user(u.getEmail()).roles("CIDADAO")).param("tipo", "TAMBEM_EXISTE"))
                .andExpect(status().isForbidden());

        assertThat(confirmacaoRepository.count()).isZero();
    }

    @Test
    void registraTrocaERetiraORelatoSemDuplicar() throws Exception {
        Denuncia d = denuncia(usuario("Autor", Role.CIDADAO));
        User u = usuario("U", Role.CIDADAO);

        votar(u, d.getId(), "TAMBEM_EXISTE").andExpect(flash().attributeExists("mensagem"));
        assertThat(confirmacaoService.resumo(d.getId(), u.getEmail()).meuVoto())
                .isEqualTo(TipoConfirmacao.TAMBEM_EXISTE);

        votar(u, d.getId(), "CONTINUA_PRESENTE");
        limpar();
        var resumo = confirmacaoService.resumo(d.getId(), u.getEmail());
        assertThat(resumo.meuVoto()).isEqualTo(TipoConfirmacao.CONTINUA_PRESENTE);
        assertThat(resumo.tambemExiste()).isZero();
        assertThat(resumo.continua()).isEqualTo(1);
        assertThat(confirmacaoRepository.count()).isEqualTo(1);

        mvc().perform(post("/denuncias/{id}/confirmar/remover", d.getId())
                        .with(user(u.getEmail()).roles("CIDADAO")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        limpar();
        assertThat(confirmacaoService.resumo(d.getId(), u.getEmail()).meuVoto()).isNull();
    }

    @Test
    void oRelatoNaoAlteraASituacaoOficial() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        Denuncia d = denuncia(autor);
        jdbc.update("UPDATE denuncias SET status = 'RESOLVIDA' WHERE id = ?", d.getId());
        limpar();

        varios(d, "CONTINUA_PRESENTE", 5);
        limpar();

        assertThat(denunciaRepository.findById(d.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusDenuncia.RESOLVIDA);
    }

    @Test
    void tipoAusenteEhRejeitado() throws Exception {
        Denuncia d = denuncia(usuario("Autor", Role.CIDADAO));
        User u = usuario("U", Role.CIDADAO);

        votar(u, d.getId(), null).andExpect(flash().attributeExists("erro"));

        assertThat(confirmacaoRepository.count()).isZero();
    }

    @Test
    void ocorrenciaOcultaOuDuplicadaOuInexistenteDa404() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        User u = usuario("U", Role.CIDADAO);
        Denuncia principal = denuncia(autor);
        Denuncia oculta = denuncia(autor);
        Denuncia filha = denuncia(autor);
        jdbc.update("UPDATE denuncias SET oculta = TRUE WHERE id = ?", oculta.getId());
        jdbc.update("UPDATE denuncias SET duplicada_de_id = ? WHERE id = ?", principal.getId(), filha.getId());
        limpar();

        votar(u, oculta.getId(), "TAMBEM_EXISTE").andExpect(status().isNotFound());
        votar(u, filha.getId(), "TAMBEM_EXISTE").andExpect(status().isNotFound());
        votar(u, 999_999_999L, "TAMBEM_EXISTE").andExpect(status().isNotFound());
    }

    @Test
    void paginaPublicaMostraContagemParaVisitanteEOpcoesParaQuemEstaLogado() throws Exception {
        Denuncia d = denuncia(usuario("Autor", Role.CIDADAO));
        User u = usuario("U", Role.CIDADAO);
        votar(u, d.getId(), "TAMBEM_EXISTE");
        limpar();

        String visitante = mvc().perform(get("/denuncias/{id}", d.getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(visitante).contains("Confirmação da comunidade")
                .contains("Também existe no meu entorno: 1").contains("não alteram a situação oficial");
        assertThat(visitante).doesNotContain("/confirmar");

        String logado = mvc().perform(get("/denuncias/{id}", d.getId()).with(user(u.getEmail()).roles("CIDADAO")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(logado).contains("/denuncias/" + d.getId() + "/confirmar").contains("Retirar meu relato");
    }

    @Test
    void ocorrenciaAgrupadaNaoMostraAConfirmacao() throws Exception {
        User autor = usuario("Autor", Role.CIDADAO);
        Denuncia principal = denuncia(autor);
        Denuncia filha = denuncia(autor);
        jdbc.update("UPDATE denuncias SET duplicada_de_id = ? WHERE id = ?", principal.getId(), filha.getId());
        limpar();

        String html = mvc().perform(get("/denuncias/{id}", filha.getId())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("Confirmação da comunidade");
    }

    @Test
    void divergenciaSoAcontecePorVozesDepoisDaResolucao() throws Exception {
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(usuario("Autor", Role.CIDADAO));
        resolver(d, serv, LocalDateTime.now().minusDays(1));

        varios(d, "CONTINUA_PRESENTE", 2);
        assertThat(listada(serv, d)).isFalse(); // abaixo do mínimo

        varios(d, "CONTINUA_PRESENTE", 1);
        assertThat(listada(serv, d)).isTrue(); // 3 pessoas
        assertThat(lista(serv).stream().filter(i -> i.getId().equals(d.getId())).findFirst().orElseThrow()
                .getContinua()).isEqualTo(3);
    }

    @Test
    void relatosAnterioresAResolucaoNaoContam() throws Exception {
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(usuario("Autor", Role.CIDADAO));
        resolver(d, serv, LocalDateTime.now().plusDays(1)); // a resolução é "depois" dos relatos

        varios(d, "CONTINUA_PRESENTE", 4);

        assertThat(listada(serv, d)).isFalse();
    }

    @Test
    void quemDizQueFoiResolvidoNeutralizaOsQueDizemQueContinua() throws Exception {
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(usuario("Autor", Role.CIDADAO));
        resolver(d, serv, LocalDateTime.now().minusDays(1));

        varios(d, "CONTINUA_PRESENTE", 3);
        varios(d, "APARENTEMENTE_RESOLVIDO", 3);
        assertThat(listada(serv, d)).isFalse(); // empate não sinaliza

        varios(d, "CONTINUA_PRESENTE", 1);
        assertThat(listada(serv, d)).isTrue(); // 4 contra 3
    }

    @Test
    void painelDaEquipeAvisaDaDivergencia() throws Exception {
        User serv = usuario("Serv", Role.SERVIDOR);
        Denuncia d = denuncia(usuario("Autor", Role.CIDADAO));
        resolver(d, serv, LocalDateTime.now().minusDays(1));
        varios(d, "CONTINUA_PRESENTE", 3);
        limpar();

        String painel = mvc().perform(get("/gestao/denuncias/{id}", d.getId())
                        .with(user(serv.getEmail()).roles("SERVIDOR")))
                .andExpect(status().isOk()).andExpect(model().attribute("divergente", true))
                .andReturn().getResponse().getContentAsString();

        assertThat(painel).contains("Reavaliação sugerida");
    }

    @Test
    void paginaDeReavaliacaoESoDaEquipe() throws Exception {
        User cidadao = usuario("Cidadao", Role.CIDADAO);

        mvc().perform(get("/gestao/reavaliacao"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc().perform(get("/gestao/reavaliacao").with(user(cidadao.getEmail()).roles("CIDADAO")))
                .andExpect(status().isForbidden());
    }
}