package com.vozpublica.vozpublica.auditoria;

import com.vozpublica.vozpublica.categoria.Categoria;
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
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

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
class AuditoriaTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditoriaRepository auditoriaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User usuario(String prefixo, Role papel) {
        return userRepository.saveAndFlush(new User("Teste " + prefixo,
                prefixo + "." + UUID.randomUUID() + "@teste.com", "hash", papel));
    }

    private List<RegistroAuditoria> registros(AcaoAuditoria acao, Long entidadeId) {
        em.flush();
        em.clear();
        return auditoriaRepository.findAll().stream()
                .filter(a -> a.getAcao() == acao && Objects.equals(a.getEntidadeId(), entidadeId))
                .toList();
    }

    @Test
    void paginaDeAuditoriaSoParaAdministrador() throws Exception {
        mvc().perform(get("/admin/auditoria"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc().perform(get("/admin/auditoria").with(user("c@teste.com").roles("CIDADAO")))
                .andExpect(status().isForbidden());
        mvc().perform(get("/admin/auditoria").with(user("s@teste.com").roles("SERVIDOR")))
                .andExpect(status().isForbidden());
        mvc().perform(get("/admin/auditoria").with(user("a@teste.com").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-auditoria"));
    }

    @Test
    void criarEAlternarUnidadeGeramAuditoria() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        String nome = "Sec-" + UUID.randomUUID();

        mvc().perform(post("/admin/unidades").with(user(admin.getEmail()).roles("ADMINISTRADOR")).with(csrf())
                        .param("nome", nome).param("tipo", "SECRETARIA"))
                .andExpect(flash().attributeExists("mensagem"));

        Unidade criada = unidadeRepository.findAll().stream()
                .filter(u -> u.getNome().equals(nome)).findFirst().orElseThrow();
        Long id = criada.getId();

        List<RegistroAuditoria> criacao = registros(AcaoAuditoria.UNIDADE_CRIADA, id);
        assertThat(criacao).hasSize(1);
        assertThat(criacao.get(0).getResponsavel().getEmail()).isEqualTo(admin.getEmail());
        assertThat(criacao.get(0).getDetalhes()).contains(nome);

        mvc().perform(post("/admin/unidades/{id}/ativa", id)
                        .with(user(admin.getEmail()).roles("ADMINISTRADOR")).with(csrf()))
                .andExpect(flash().attributeExists("mensagem"));
        assertThat(registros(AcaoAuditoria.UNIDADE_DESATIVADA, id)).hasSize(1);

        mvc().perform(post("/admin/unidades/{id}/ativa", id)
                        .with(user(admin.getEmail()).roles("ADMINISTRADOR")).with(csrf()))
                .andExpect(flash().attributeExists("mensagem"));
        assertThat(registros(AcaoAuditoria.UNIDADE_REATIVADA, id)).hasSize(1);
    }

    @Test
    void alterarPrazoDaCategoriaRegistraValorAntigoENovo() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        Categoria categoria = categoriaRepository.findByAtivaTrueOrderByNome().get(0);
        Long id = categoria.getId();
        int original = categoria.getPrazoReferenciaDias();
        int novo = original == 12 ? 13 : 12;

        mvc().perform(post("/admin/categorias/{id}/prazo", id)
                        .with(user(admin.getEmail()).roles("ADMINISTRADOR")).with(csrf())
                        .param("prazoDias", String.valueOf(novo)))
                .andExpect(flash().attributeExists("mensagem"));

        List<RegistroAuditoria> lista = registros(AcaoAuditoria.PRAZO_CATEGORIA_ALTERADO, id);
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).getDetalhes()).contains(original + " → " + novo);
        assertThat(lista.get(0).getResponsavel().getEmail()).isEqualTo(admin.getEmail());
    }

    @Test
    void prioridadeOficialGeraAuditoriaComAJustificativa() throws Exception {
        User autor = usuario("autor", Role.CIDADAO);
        User servidor = usuario("serv", Role.SERVIDOR);
        Denuncia d = denunciaRepository.saveAndFlush(new Denuncia(autor,
                categoriaRepository.findByAtivaTrueOrderByNome().get(0),
                "Ocorrência de auditoria", "Descrição de teste do problema.",
                null, "Boa Vista", "RPA 1 - Centro", null, null, Prioridade.MEDIA));

        mvc().perform(post("/gestao/denuncias/{id}/prioridade", d.getId())
                        .with(user(servidor.getEmail()).roles("SERVIDOR")).with(csrf())
                        .param("prioridade", "URGENTE").param("observacao", "Risco à segurança"))
                .andExpect(flash().attributeExists("mensagem"));

        List<RegistroAuditoria> lista = registros(AcaoAuditoria.PRIORIDADE_OFICIAL_DEFINIDA, d.getId());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).getJustificativa()).isEqualTo("Risco à segurança");
        assertThat(lista.get(0).getDetalhes()).contains("não definida → URGENTE");
        assertThat(lista.get(0).getResponsavel().getEmail()).isEqualTo(servidor.getEmail());
    }

    @Test
    void acaoRejeitadaNaoGeraAuditoria() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        Long categoriaId = categoriaRepository.findByAtivaTrueOrderByNome().get(0).getId();
        long antes = auditoriaRepository.count();

        mvc().perform(post("/admin/categorias/{id}/prazo", categoriaId)
                        .with(user(admin.getEmail()).roles("ADMINISTRADOR")).with(csrf())
                        .param("prazoDias", "0"))
                .andExpect(flash().attributeExists("erro"));

        em.flush();
        assertThat(auditoriaRepository.count()).isEqualTo(antes);
    }

    @Test
    void registroDeAuditoriaNaoPodeSerAlteradoPorSql() {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        RegistroAuditoria r = auditoriaRepository.saveAndFlush(new RegistroAuditoria(
                admin, AcaoAuditoria.UNIDADE_CRIADA, "UNIDADE", 1L, null, "original"));

        assertThatThrownBy(() -> jdbc.update("UPDATE auditoria SET detalhes = 'alterado' WHERE id = ?", r.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void registroDeAuditoriaNaoPodeSerApagadoPorSql() {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        RegistroAuditoria r = auditoriaRepository.saveAndFlush(new RegistroAuditoria(
                admin, AcaoAuditoria.UNIDADE_CRIADA, "UNIDADE", 1L, null, "original"));

        assertThatThrownBy(() -> jdbc.update("DELETE FROM auditoria WHERE id = ?", r.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtrosDaPaginaFuncionam() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        mvc().perform(post("/admin/unidades").with(user(admin.getEmail()).roles("ADMINISTRADOR")).with(csrf())
                .param("nome", "Sec-" + UUID.randomUUID()).param("tipo", "SECRETARIA"));
        em.flush();
        em.clear();

        // ação + responsável (e-mail em maiúsculas: a busca ignora caixa)
        Page<RegistroAuditoria> doAdmin = (Page<RegistroAuditoria>) mvc().perform(
                        get("/admin/auditoria")
                                .with(user("a@teste.com").roles("ADMINISTRADOR"))
                                .param("acao", "UNIDADE_CRIADA")
                                .param("responsavel", admin.getEmail().toUpperCase()))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("page");
        assertThat(doAdmin.getContent()).isNotEmpty()
                .allMatch(r -> r.getAcao() == AcaoAuditoria.UNIDADE_CRIADA
                        && r.getResponsavel().getEmail().equals(admin.getEmail()));

        // ação diferente da que esse administrador fez
        Page<RegistroAuditoria> outra = (Page<RegistroAuditoria>) mvc().perform(
                        get("/admin/auditoria")
                                .with(user("a@teste.com").roles("ADMINISTRADOR"))
                                .param("acao", "PAPEL_ALTERADO")
                                .param("responsavel", admin.getEmail()))
                .andReturn().getModelAndView().getModel().get("page");
        assertThat(outra.getContent()).isEmpty();

        // período no futuro
        Page<RegistroAuditoria> futuro = (Page<RegistroAuditoria>) mvc().perform(
                        get("/admin/auditoria")
                                .with(user("a@teste.com").roles("ADMINISTRADOR"))
                                .param("de", "2999-01-01"))
                .andReturn().getModelAndView().getModel().get("page");
        assertThat(futuro.getContent()).isEmpty();

        // valores inválidos são ignorados, sem erro
        mvc().perform(get("/admin/auditoria")
                        .with(user("a@teste.com").roles("ADMINISTRADOR"))
                        .param("acao", "XYZ").param("de", "2020-13-45"))
                .andExpect(status().isOk());
    }
}