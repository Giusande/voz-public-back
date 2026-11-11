package com.vozpublica.vozpublica.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AuthSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void paginasPublicasAbremSemLogin() throws Exception {
        mvc().perform(get("/")).andExpect(status().isOk());
        mvc().perform(get("/login")).andExpect(status().isOk());
        mvc().perform(get("/cadastro")).andExpect(status().isOk());
    }

    @Test
    void rotaPrivadaRedirecionaVisitanteParaLogin() throws Exception {
        mvc().perform(get("/minhas-denuncias"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "CIDADAO")
    void cidadaoNaoAcessaAreaAdmin() throws Exception {
        mvc().perform(get("/admin/teste")).andExpect(status().isForbidden());
    }

    @Test
    void cadastroSemTokenCsrfEhRejeitado() throws Exception {
        mvc().perform(post("/cadastro")
                        .param("name", "Ana")
                        .param("email", "ana.csrf@teste.com")
                        .param("password", "senha1234")
                        .param("confirmPassword", "senha1234"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cadastroValidoCriaCidadaoComSenhaComHash() throws Exception {
        mvc().perform(post("/cadastro").with(csrf())
                        .param("name", "Ana Souza")
                        .param("email", "Ana.Valida@Teste.com")
                        .param("password", "senha1234")
                        .param("confirmPassword", "senha1234"))
                .andExpect(status().is3xxRedirection());

        User salvo = userRepository.findByEmail("ana.valida@teste.com").orElseThrow();
        assertThat(salvo.getRole()).isEqualTo(Role.CIDADAO);
        assertThat(salvo.getPasswordHash()).startsWith("$2").isNotEqualTo("senha1234");
    }

    @Test
    void naoEhPossivelSePromoverAdministradorNoCadastro() throws Exception {
        mvc().perform(post("/cadastro").with(csrf())
                        .param("name", "Mallory")
                        .param("email", "mallory@teste.com")
                        .param("password", "senha1234")
                        .param("confirmPassword", "senha1234")
                        .param("role", "ADMINISTRADOR")
                        .param("papel", "ADMINISTRADOR"))
                .andExpect(status().is3xxRedirection());

        User salvo = userRepository.findByEmail("mallory@teste.com").orElseThrow();
        assertThat(salvo.getRole()).isEqualTo(Role.CIDADAO);
    }

    @Test
    void emailDuplicadoEhRejeitadoIgnorandoMaiusculas() throws Exception {
        var m = mvc();
        m.perform(post("/cadastro").with(csrf())
                .param("name", "Bia")
                .param("email", "bia@teste.com")
                .param("password", "senha1234")
                .param("confirmPassword", "senha1234"));

        m.perform(post("/cadastro").with(csrf())
                        .param("name", "Outra Bia")
                        .param("email", "BIA@TESTE.COM")
                        .param("password", "senha1234")
                        .param("confirmPassword", "senha1234"))
                .andExpect(status().isOk())          // volta ao formulário com erro
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("registerForm", "email"));
    }

    @Test
    void senhasDiferentesSaoRejeitadas() throws Exception {
        mvc().perform(post("/cadastro").with(csrf())
                        .param("name", "Caio")
                        .param("email", "caio@teste.com")
                        .param("password", "senha1234")
                        .param("confirmPassword", "outrasenha"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("registerForm", "confirmPassword"));

        assertThat(userRepository.existsByEmail("caio@teste.com")).isFalse();
    }
}