package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.encaminhamento.Encaminhamento;
import com.vozpublica.vozpublica.encaminhamento.EncaminhamentoRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class MinhasDenunciasTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EncaminhamentoRepository encaminhamentoRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User usuario(String nome, Role papel) {
        return userRepository.saveAndFlush(new User(nome + " " + UUID.randomUUID(),
                "minhas." + UUID.randomUUID() + "@teste.com", "hash", papel));
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

    private MvcResult minhas(User u, String... params) throws Exception {
        var req = get("/minhas-denuncias").with(user(u.getEmail()).roles(u.getRole().name()));
        for (int i = 0; i < params.length; i += 2) req.param(params[i], params[i + 1]);
        return mvc().perform(req).andExpect(status().isOk()).andReturn();
    }

    private String html(User u) throws Exception {
        return minhas(u).getResponse().getContentAsString();
    }

    @Test
    void exigeLogin() throws Exception {
        mvc().perform(get("/minhas-denuncias"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void mostraSoAsOcorrenciasDoAutor() throws Exception {
        User eu = usuario("Eu", Role.CIDADAO);
        User outro = usuario("Outro", Role.CIDADAO);
        Denuncia minha = denuncia(eu, "Minha " + UUID.randomUUID());
        Denuncia dele = denuncia(outro, "Dele " + UUID.randomUUID());
        limpar();

        String pagina = html(eu);

        assertThat(pagina).contains(minha.getTitulo()).doesNotContain(dele.getTitulo());
    }

    @Test
    void ocultaApareceComAvisoESemLink() throws Exception {
        User eu = usuario("Eu", Role.CIDADAO);
        Denuncia d = denuncia(eu, "Oculta " + UUID.randomUUID());
        jdbc.update("UPDATE denuncias SET oculta = TRUE WHERE id = ?", d.getId());
        limpar();

        String pagina = html(eu);

        assertThat(pagina).contains(d.getTitulo()).contains("Oculta pela moderação");
        assertThat(pagina).doesNotContain("href=\"/denuncias/" + d.getId() + "\"");
    }

    @Test
    void duplicadaMostraASituacaoDaPrincipal() throws Exception {
        User eu = usuario("Eu", Role.CIDADAO);
        User outro = usuario("Outro", Role.CIDADAO);
        Denuncia principal = denuncia(outro, "Principal " + UUID.randomUUID());
        Denuncia minha = denuncia(eu, "Filha " + UUID.randomUUID());
        jdbc.update("UPDATE denuncias SET status = 'EM_EXECUCAO' WHERE id = ?", principal.getId());
        jdbc.update("UPDATE denuncias SET duplicada_de_id = ? WHERE id = ?", principal.getId(), minha.getId());
        limpar();

        String pagina = html(eu);

        assertThat(pagina).contains("Agrupada à ocorrência").contains("#" + principal.getId())
                .contains("situação da principal").contains("Em execução");
    }

    @Test
    void mostraSoAUnidadeAtualDoEncaminhamentoSemServidorNemObservacao() throws Exception {
        User eu = usuario("Eu", Role.CIDADAO);
        User gestor = usuario("Gestor", Role.SERVIDOR);
        User responsavel = usuario("Responsavel", Role.SERVIDOR);
        Denuncia d = denuncia(eu, "Com encaminhamento " + UUID.randomUUID());
        Unidade antiga = unidadeRepository.saveAndFlush(
                new Unidade("UnidadeAntiga-" + UUID.randomUUID(), TipoUnidade.SECRETARIA, null));
        Unidade atual = unidadeRepository.saveAndFlush(
                new Unidade("UnidadeAtual-" + UUID.randomUUID(), TipoUnidade.SECRETARIA, null));
        encaminhamentoRepository.saveAndFlush(new Encaminhamento(d.getId(), antiga, null, null, null, gestor));
        encaminhamentoRepository.saveAndFlush(
                new Encaminhamento(d.getId(), atual, responsavel, null, "nota interna sigilosa", gestor));
        limpar();

        String pagina = html(eu);

        assertThat(pagina).contains(atual.getNome());
        assertThat(pagina).doesNotContain(antiga.getNome()).doesNotContain("nota interna sigilosa")
                .doesNotContain(responsavel.getName()).doesNotContain(gestor.getName());
    }

    @Test
    void paginaDeDezEPaginaSeguinteTemOResto() throws Exception {
        User eu = usuario("Eu", Role.CIDADAO);
        for (int i = 0; i < 11; i++) denuncia(eu, "Item " + i + " " + UUID.randomUUID());
        limpar();

        var primeira = (MinhasDenunciasService.Visao) minhas(eu).getModelAndView().getModel().get("v");
        var segunda = (MinhasDenunciasService.Visao) minhas(eu, "pagina", "1")
                .getModelAndView().getModel().get("v");

        assertThat(primeira.page().getTotalElements()).isEqualTo(11);
        assertThat(primeira.page().getContent()).hasSize(10);
        assertThat(segunda.page().getContent()).hasSize(1);
    }

    @Test
    void resolvidaOrientaSobreAsEvidencias() throws Exception {
        User eu = usuario("Eu", Role.CIDADAO);
        Denuncia d = denuncia(eu, "Resolvida " + UUID.randomUUID());
        jdbc.update("UPDATE denuncias SET status = 'RESOLVIDA' WHERE id = ?", d.getId());
        limpar();

        assertThat(html(eu)).contains("Marcada como resolvida").contains("evidências");
    }

    @Test
    void menuTemOsLinksSoParaQuemEstaLogado() throws Exception {
        User eu = usuario("Eu", Role.CIDADAO);

        String visitante = mvc().perform(get("/denuncias")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(visitante).doesNotContain("/minhas-denuncias").doesNotContain("Meu perfil");

        String logado = mvc().perform(get("/denuncias").with(user(eu.getEmail()).roles("CIDADAO")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(logado).contains("/minhas-denuncias").contains("Meu perfil");
    }
}