package com.vozpublica.vozpublica.encaminhamento;

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
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class EncaminhamentoTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EncaminhamentoRepository encaminhamentoRepository;
    @Autowired private AtrasoRepository atrasoRepository;
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
                "Ocorrência " + UUID.randomUUID(), "Descrição de teste do problema.",
                null, "Boa Vista", "RPA 1 - Centro", null, null, Prioridade.MEDIA));
    }

    private Unidade secretaria(String base) {
        return unidadeRepository.saveAndFlush(
                new Unidade(base + "-" + UUID.randomUUID(), TipoUnidade.SECRETARIA, null));
    }

    private ResultActions encaminhar(User quem, Role papel, Long id, Long unidadeId,
                                     Long servidorId, String prazo, String obs) throws Exception {
        var b = post("/gestao/denuncias/{id}/encaminhar", id)
                .with(user(quem.getEmail()).roles(papel.name())).with(csrf());
        if (unidadeId != null) b.param("unidadeId", String.valueOf(unidadeId));
        if (servidorId != null) b.param("servidorId", String.valueOf(servidorId));
        if (prazo != null) b.param("prazo", prazo);
        if (obs != null) b.param("observacao", obs);
        return mvc().perform(b);
    }

    private void limparCache() {
        em.flush();
        em.clear();
    }

    @Test
    void visitanteECidadaoNaoEncaminham() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        Denuncia d = denuncia(autor);
        Unidade sec = secretaria("Sec");

        mvc().perform(post("/gestao/denuncias/{id}/encaminhar", d.getId()).with(csrf())
                        .param("unidadeId", String.valueOf(sec.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        encaminhar(autor, Role.CIDADAO, d.getId(), sec.getId(), null, null, null)
                .andExpect(status().isForbidden());

        assertThat(encaminhamentoRepository.findByDenunciaIdOrderByIdDesc(d.getId())).isEmpty();
    }

    @Test
    void servidorEncaminhaEOsDadosFicamRegistrados() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User gestor = usuario("gestor", Role.SERVIDOR);
        User responsavel = usuario("resp", Role.SERVIDOR);
        Unidade sec = secretaria("Sec");
        LocalDate prazo = LocalDate.now().plusDays(10);

        encaminhar(gestor, Role.SERVIDOR, d.getId(), sec.getId(), responsavel.getId(),
                prazo.toString(), "  Verificar no local.  ")
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("mensagem"));

        List<Encaminhamento> lista = encaminhamentoRepository.findByDenunciaIdOrderByIdDesc(d.getId());
        assertThat(lista).hasSize(1);
        Encaminhamento e = lista.get(0);
        assertThat(e.getUnidade().getId()).isEqualTo(sec.getId());
        assertThat(e.getServidor().getEmail()).isEqualTo(responsavel.getEmail());
        assertThat(e.getResponsavel().getEmail()).isEqualTo(gestor.getEmail());
        assertThat(e.getPrazoEspecifico()).isEqualTo(prazo);
        assertThat(e.getObservacao()).isEqualTo("Verificar no local.");
    }

    @Test
    void novoEncaminhamentoPreservaOHistoricoDeResponsabilidade() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User gestor = usuario("gestor", Role.SERVIDOR);
        Unidade primeira = secretaria("Primeira");
        Unidade segunda = secretaria("Segunda");

        encaminhar(gestor, Role.SERVIDOR, d.getId(), primeira.getId(), null, null, null)
                .andExpect(flash().attributeExists("mensagem"));
        encaminhar(gestor, Role.SERVIDOR, d.getId(), segunda.getId(), null, null, null)
                .andExpect(flash().attributeExists("mensagem"));

        List<Encaminhamento> lista = encaminhamentoRepository.findByDenunciaIdOrderByIdDesc(d.getId());
        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).getUnidade().getId()).isEqualTo(segunda.getId());  // atual
        assertThat(lista.get(1).getUnidade().getId()).isEqualTo(primeira.getId()); // anterior preservado
    }

    @Test
    void encaminhamentoIgualAoAtualEhRejeitado() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User gestor = usuario("gestor", Role.SERVIDOR);
        Unidade sec = secretaria("Sec");

        encaminhar(gestor, Role.SERVIDOR, d.getId(), sec.getId(), null, null, null)
                .andExpect(flash().attributeExists("mensagem"));
        encaminhar(gestor, Role.SERVIDOR, d.getId(), sec.getId(), null, null, null)
                .andExpect(flash().attributeExists("erro"));

        assertThat(encaminhamentoRepository.findByDenunciaIdOrderByIdDesc(d.getId())).hasSize(1);
    }

    @Test
    void servidorResponsavelPrecisaSerDaEquipe() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User gestor = usuario("gestor", Role.SERVIDOR);
        User cidadao = usuario("cidadao", Role.CIDADAO);
        Unidade sec = secretaria("Sec");

        encaminhar(gestor, Role.SERVIDOR, d.getId(), sec.getId(), cidadao.getId(), null, null)
                .andExpect(flash().attributeExists("erro"));

        assertThat(encaminhamentoRepository.findByDenunciaIdOrderByIdDesc(d.getId())).isEmpty();
    }

    @Test
    void unidadeDesativadaEhRejeitada() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User gestor = usuario("gestor", Role.SERVIDOR);
        Unidade sec = secretaria("Sec");
        jdbc.update("UPDATE unidades SET ativa = FALSE WHERE id = ?", sec.getId());
        limparCache();

        encaminhar(gestor, Role.SERVIDOR, d.getId(), sec.getId(), null, null, null)
                .andExpect(flash().attributeExists("erro"));

        assertThat(encaminhamentoRepository.findByDenunciaIdOrderByIdDesc(d.getId())).isEmpty();
    }

    @Test
    void ocorrenciaEncerradaNaoPodeSerEncaminhada() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User gestor = usuario("gestor", Role.SERVIDOR);
        Unidade sec = secretaria("Sec");
        jdbc.update("UPDATE denuncias SET status = 'RESOLVIDA' WHERE id = ?", d.getId());
        limparCache();

        encaminhar(gestor, Role.SERVIDOR, d.getId(), sec.getId(), null, null, null)
                .andExpect(flash().attributeExists("erro"));

        assertThat(encaminhamentoRepository.findByDenunciaIdOrderByIdDesc(d.getId())).isEmpty();
    }

    @Test
    void prazoEspecificoNoPassadoOuMuitoLongeEhRejeitado() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User gestor = usuario("gestor", Role.SERVIDOR);
        Unidade sec = secretaria("Sec");

        encaminhar(gestor, Role.SERVIDOR, d.getId(), sec.getId(), null,
                LocalDate.now().minusDays(1).toString(), null)
                .andExpect(flash().attributeExists("erro"));
        encaminhar(gestor, Role.SERVIDOR, d.getId(), sec.getId(), null,
                LocalDate.now().plusDays(400).toString(), null)
                .andExpect(flash().attributeExists("erro"));

        assertThat(encaminhamentoRepository.findByDenunciaIdOrderByIdDesc(d.getId())).isEmpty();
    }

    @Test
    void encaminhamentoGravadoNaoPodeSerAlteradoNemPorSql() {
        User gestor = usuario("gestor", Role.SERVIDOR);
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        Unidade sec = secretaria("Sec");
        encaminhamentoRepository.saveAndFlush(new Encaminhamento(d.getId(), sec, null, null, "original", gestor));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE encaminhamentos SET observacao = 'alterada' WHERE denuncia_id = ?", d.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void paginaPublicaMostraAUnidadeMasNaoOServidorNemAObservacao() throws Exception {
        Denuncia d = denuncia(usuario("autor", Role.CIDADAO));
        User gestor = usuario("gestor", Role.SERVIDOR);
        User responsavel = usuario("resp", Role.SERVIDOR);
        Unidade sec = secretaria("SecPublica");
        encaminhamentoRepository.saveAndFlush(
                new Encaminhamento(d.getId(), sec, responsavel, null, "nota interna sigilosa", gestor));
        limparCache();

        String html = mvc().perform(get("/denuncias/{id}", d.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(sec.getNome());
        assertThat(html).doesNotContain(responsavel.getName());
        assertThat(html).doesNotContain(gestor.getName());
        assertThat(html).doesNotContain("nota interna sigilosa");
    }

    @Test
    void listaDeAtrasadasEoCalculoEmJavaConcordam() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User gestor = usuario("gestor", Role.SERVIDOR);
        Unidade sec = secretaria("Sec");
        int diasCategoria = categoriaRepository.findByAtivaTrueOrderByNome().get(0).getPrazoReferenciaDias();

        Denuncia antigaAberta = denuncia(autor);
        Denuncia antigaResolvida = denuncia(autor);
        Denuncia recente = denuncia(autor);
        Denuncia antigaComPrazoNovo = denuncia(autor);

        Timestamp antiga = Timestamp.valueOf(LocalDateTime.of(2020, 1, 1, 12, 0));
        for (Denuncia d : List.of(antigaAberta, antigaResolvida, antigaComPrazoNovo)) {
            jdbc.update("UPDATE denuncias SET data_criacao = ? WHERE id = ?", antiga, d.getId());
        }
        jdbc.update("UPDATE denuncias SET status = 'RESOLVIDA' WHERE id = ?", antigaResolvida.getId());
        encaminhamentoRepository.saveAndFlush(new Encaminhamento(
                antigaComPrazoNovo.getId(), sec, null, LocalDate.now().plusDays(5), null, gestor));
        limparCache();

        var linhas = atrasoRepository.buscarAtrasadas(LocalDate.now(), PageRequest.of(0, 10000)).getContent();
        List<Long> ids = linhas.stream().map(DenunciaAtrasada::getId).toList();

        // SQL
        assertThat(ids).contains(antigaAberta.getId());
        assertThat(ids).doesNotContain(antigaResolvida.getId(), recente.getId(), antigaComPrazoNovo.getId());

        // o prazo calculado no SQL bate com o da regra em Java (criação + dias da categoria)
        String prazoSql = linhas.stream().filter(l -> l.getId().equals(antigaAberta.getId()))
                .findFirst().orElseThrow().getPrazo();
        assertThat(prazoSql).isEqualTo(LocalDate.of(2020, 1, 1).plusDays(diasCategoria).toString());

        // Java
        LocalDate hoje = LocalDate.now();
        assertThat(atrasadaEmJava(antigaAberta.getId(), hoje)).isTrue();
        assertThat(atrasadaEmJava(antigaResolvida.getId(), hoje)).isFalse();
        assertThat(atrasadaEmJava(recente.getId(), hoje)).isFalse();
        assertThat(atrasadaEmJava(antigaComPrazoNovo.getId(), hoje)).isFalse();
    }

    private boolean atrasadaEmJava(Long denunciaId, LocalDate hoje) {
        Denuncia d = denunciaRepository.findComDetalhesById(denunciaId).orElseThrow();
        Encaminhamento atual = encaminhamentoRepository.findFirstByDenunciaIdOrderByIdDesc(denunciaId).orElse(null);
        return Prazo.calcular(d, atual, hoje).atrasada();
    }

    @Test
    void paginaDeAtrasadasSoParaAEquipe() throws Exception {
        User cidadao = usuario("cidadao", Role.CIDADAO);
        User servidor = usuario("serv", Role.SERVIDOR);

        mvc().perform(get("/gestao/atrasadas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mvc().perform(get("/gestao/atrasadas").with(user(cidadao.getEmail()).roles("CIDADAO")))
                .andExpect(status().isForbidden());

        mvc().perform(get("/gestao/atrasadas").with(user(servidor.getEmail()).roles("SERVIDOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("gestao-atrasadas"))
                .andExpect(model().attributeExists("page"));
    }
}