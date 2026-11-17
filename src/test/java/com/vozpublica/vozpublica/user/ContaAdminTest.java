package com.vozpublica.vozpublica.user;

import com.vozpublica.vozpublica.auditoria.AcaoAuditoria;
import com.vozpublica.vozpublica.auditoria.AuditoriaRepository;
import com.vozpublica.vozpublica.auditoria.RegistroAuditoria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
class ContaAdminTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditoriaRepository auditoriaRepository;
    @Autowired private ContaAdminService contaService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User usuario(String prefixo, Role papel) {
        return userRepository.saveAndFlush(new User("Teste " + prefixo,
                prefixo + "." + UUID.randomUUID() + "@teste.com", "hash", papel));
    }

    private User recarregar(Long id) {
        em.flush();
        em.clear();
        return userRepository.findById(id).orElseThrow();
    }

    private ResultActions postar(User quem, Role sessao, String caminho, String... pares) throws Exception {
        var b = post(caminho).with(user(quem.getEmail()).roles(sessao.name())).with(csrf());
        for (int i = 0; i < pares.length; i += 2) {
            b.param(pares[i], pares[i + 1]);
        }
        return mvc().perform(b);
    }

    private List<RegistroAuditoria> registros(AcaoAuditoria acao, Long alvoId) {
        em.flush();
        em.clear();
        return auditoriaRepository.findAll().stream()
                .filter(a -> a.getAcao() == acao && Objects.equals(a.getEntidadeId(), alvoId))
                .toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void soAdministradorAcessaAGestaoDeContas() throws Exception {
        mvc().perform(get("/admin/usuarios"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc().perform(get("/admin/usuarios").with(user("c@teste.com").roles("CIDADAO")))
                .andExpect(status().isForbidden());
        mvc().perform(get("/admin/usuarios").with(user("s@teste.com").roles("SERVIDOR")))
                .andExpect(status().isForbidden());

        Page<User> page = (Page<User>) mvc().perform(
                        get("/admin/usuarios").with(user("a@teste.com").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-usuarios"))
                .andReturn().getModelAndView().getModel().get("page");
        assertThat(page).isNotNull();
    }

    @Test
    void cidadaoEServidorNaoAlteramContas() throws Exception {
        User cidadao = usuario("cidadao", Role.CIDADAO);
        User servidor = usuario("serv", Role.SERVIDOR);
        User alvo = usuario("alvo", Role.CIDADAO);
        String base = "/admin/usuarios/" + alvo.getId();

        postar(cidadao, Role.CIDADAO, base + "/papel", "papel", "ADMINISTRADOR", "justificativa", "x")
                .andExpect(status().isForbidden());
        postar(servidor, Role.SERVIDOR, base + "/papel", "papel", "ADMINISTRADOR", "justificativa", "x")
                .andExpect(status().isForbidden());
        postar(servidor, Role.SERVIDOR, base + "/suspender", "justificativa", "x")
                .andExpect(status().isForbidden());

        User relido = recarregar(alvo.getId());
        assertThat(relido.getRole()).isEqualTo(Role.CIDADAO);
        assertThat(relido.isEnabled()).isTrue();
    }

    @Test
    void administradorMudaPerfilEAAuditoriaGuardaJustificativaEResponsavel() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        User alvo = usuario("alvo", Role.CIDADAO);

        postar(admin, Role.ADMINISTRADOR, "/admin/usuarios/" + alvo.getId() + "/papel",
                "papel", "SERVIDOR", "justificativa", "  Nomeado para a equipe  ")
                .andExpect(flash().attributeExists("mensagem"));

        assertThat(recarregar(alvo.getId()).getRole()).isEqualTo(Role.SERVIDOR);

        List<RegistroAuditoria> lista = registros(AcaoAuditoria.PAPEL_ALTERADO, alvo.getId());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).getJustificativa()).isEqualTo("Nomeado para a equipe");
        assertThat(lista.get(0).getDetalhes()).contains("CIDADAO → SERVIDOR");
        assertThat(lista.get(0).getDetalhes()).doesNotContain(alvo.getEmail()); // sem dados pessoais
        assertThat(lista.get(0).getResponsavel().getEmail()).isEqualTo(admin.getEmail());
    }

    @Test
    void justificativaEObrigatoriaENadaMudaSemEla() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        User alvo = usuario("alvo", Role.CIDADAO);
        long antes = auditoriaRepository.count();

        postar(admin, Role.ADMINISTRADOR, "/admin/usuarios/" + alvo.getId() + "/papel",
                "papel", "SERVIDOR", "justificativa", "   ")
                .andExpect(flash().attributeExists("erro"));
        postar(admin, Role.ADMINISTRADOR, "/admin/usuarios/" + alvo.getId() + "/suspender",
                "justificativa", "")
                .andExpect(flash().attributeExists("erro"));

        User relido = recarregar(alvo.getId());
        assertThat(relido.getRole()).isEqualTo(Role.CIDADAO);
        assertThat(relido.isEnabled()).isTrue();
        assertThat(auditoriaRepository.count()).isEqualTo(antes);
    }

    @Test
    void administradorNaoAlteraNemSuspendeASiMesmo() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        usuario("outro", Role.ADMINISTRADOR);
        String base = "/admin/usuarios/" + admin.getId();

        postar(admin, Role.ADMINISTRADOR, base + "/papel", "papel", "CIDADAO", "justificativa", "teste")
                .andExpect(flash().attributeExists("erro"));
        postar(admin, Role.ADMINISTRADOR, base + "/suspender", "justificativa", "teste")
                .andExpect(flash().attributeExists("erro"));

        User relido = recarregar(admin.getId());
        assertThat(relido.getRole()).isEqualTo(Role.ADMINISTRADOR);
        assertThat(relido.isEnabled()).isTrue();
    }

    @Test
    void suspenderEReativarFuncionamEFicamAuditados() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        User alvo = usuario("alvo", Role.CIDADAO);
        String base = "/admin/usuarios/" + alvo.getId();

        postar(admin, Role.ADMINISTRADOR, base + "/suspender", "justificativa", "Comentários abusivos")
                .andExpect(flash().attributeExists("mensagem"));
        assertThat(recarregar(alvo.getId()).isEnabled()).isFalse();
        assertThat(registros(AcaoAuditoria.USUARIO_SUSPENSO, alvo.getId())).hasSize(1);

        postar(admin, Role.ADMINISTRADOR, base + "/reativar", "justificativa", "Recurso aceito")
                .andExpect(flash().attributeExists("mensagem"));
        assertThat(recarregar(alvo.getId()).isEnabled()).isTrue();
        List<RegistroAuditoria> reativacao = registros(AcaoAuditoria.USUARIO_REATIVADO, alvo.getId());
        assertThat(reativacao).hasSize(1);
        assertThat(reativacao.get(0).getJustificativa()).isEqualTo("Recurso aceito");
    }

    @Test
    void repetirSuspensaoOuReativacaoEhRejeitado() throws Exception {
        User admin = usuario("admin", Role.ADMINISTRADOR);
        User ativo = usuario("ativo", Role.CIDADAO);
        User suspenso = usuario("suspenso", Role.CIDADAO);
        jdbc.update("UPDATE usuarios SET ativo = FALSE WHERE id = ?", suspenso.getId());
        em.flush();
        em.clear();

        postar(admin, Role.ADMINISTRADOR, "/admin/usuarios/" + suspenso.getId() + "/suspender",
                "justificativa", "x").andExpect(flash().attributeExists("erro"));
        postar(admin, Role.ADMINISTRADOR, "/admin/usuarios/" + ativo.getId() + "/reativar",
                "justificativa", "x").andExpect(flash().attributeExists("erro"));
    }

    @Test
    void usuarioSuspensoNaoConsegueEntrar() throws Exception {
        String email = "suspenso." + UUID.randomUUID() + "@teste.com";
        User u = userRepository.saveAndFlush(
                new User("Suspenso", email, passwordEncoder.encode("senha1234"), Role.CIDADAO));
        jdbc.update("UPDATE usuarios SET ativo = FALSE WHERE id = ?", u.getId());
        em.flush();
        em.clear();

        mvc().perform(post("/login").with(csrf())
                        .param("username", email).param("password", "senha1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void sessaoJaAbertaDeUsuarioSuspensoEEncerrada() throws Exception {
        User u = usuario("logado", Role.CIDADAO);
        jdbc.update("UPDATE usuarios SET ativo = FALSE WHERE id = ?", u.getId());
        em.flush();
        em.clear();

        mvc().perform(get("/denuncias/nova").with(user(u.getEmail()).roles("CIDADAO")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?encerrada"));
    }

    @Test
    void sessaoComPerfilDesatualizadoEEncerrada() throws Exception {
        // no banco a pessoa já foi rebaixada para CIDADAO, mas a sessão ainda diz ADMINISTRADOR
        User u = usuario("rebaixado", Role.CIDADAO);

        mvc().perform(get("/admin/usuarios").with(user(u.getEmail()).roles("ADMINISTRADOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?encerrada"));
    }

    @Test
    @WithMockUser(roles = "SERVIDOR")
    void servicoRecusaChamadaDeNaoAdministradorMesmoSemPassarPeloControlador() {
        assertThatThrownBy(() -> contaService.suspender(1L, "x", "qualquer@teste.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "ator.ultimo@teste.com", roles = "ADMINISTRADOR")
    void ultimoAdministradorAtivoNaoPodeSerRebaixadoNemSuspenso() {
        // simula a corrida: quem age já não é administrador ativo no banco
        jdbc.update("UPDATE usuarios SET ativo = FALSE WHERE papel = 'ADMINISTRADOR'");
        em.flush();
        em.clear();
        userRepository.saveAndFlush(new User("Ator", "ator.ultimo@teste.com", "hash", Role.SERVIDOR));
        User ultimo = userRepository.saveAndFlush(
                new User("Ultimo", "ultimo." + UUID.randomUUID() + "@teste.com", "hash", Role.ADMINISTRADOR));

        assertThatThrownBy(() -> contaService.alterarPapel(
                ultimo.getId(), Role.CIDADAO, "teste", "ator.ultimo@teste.com"))
                .isInstanceOf(ContaInvalidaException.class)
                .hasMessageContaining("último administrador ativo");
        assertThatThrownBy(() -> contaService.suspender(
                ultimo.getId(), "teste", "ator.ultimo@teste.com"))
                .isInstanceOf(ContaInvalidaException.class)
                .hasMessageContaining("último administrador ativo");

        User relido = recarregar(ultimo.getId());
        assertThat(relido.getRole()).isEqualTo(Role.ADMINISTRADOR);
        assertThat(relido.isEnabled()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buscaPorNomeOuEmailIgnoraMaiusculas() throws Exception {
        String marca = UUID.randomUUID().toString();
        User alvo = userRepository.saveAndFlush(
                new User("Fulano", "busca." + marca + "@teste.com", "hash", Role.CIDADAO));

        Page<User> page = (Page<User>) mvc().perform(get("/admin/usuarios")
                        .with(user("a@teste.com").roles("ADMINISTRADOR"))
                        .param("busca", marca.toUpperCase()))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("page");

        assertThat(page.getContent()).extracting(User::getId).containsExactly(alvo.getId());
    }
}