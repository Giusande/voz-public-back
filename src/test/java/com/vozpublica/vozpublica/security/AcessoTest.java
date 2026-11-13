package com.vozpublica.vozpublica.security;

import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AcessoTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void acessoComRetornoValidoGuardaNaSessaoERedirecionaAoLogin() throws Exception {
        MockHttpSession sessao = new MockHttpSession();

        mvc().perform(get("/acesso").param("retorno", "/denuncias/7").param("destino", "login").session(sessao))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        assertThat(sessao.getAttribute(LoginSuccessHandler.ATRIBUTO_RETORNO)).isEqualTo("/denuncias/7");
    }

    @Test
    void acessoIgnoraRetornoParaOutroSite() throws Exception {
        MockHttpSession sessao = new MockHttpSession();

        mvc().perform(get("/acesso").param("retorno", "https://site-malicioso.com").session(sessao))
                .andExpect(redirectedUrl("/login"));
        mvc().perform(get("/acesso").param("retorno", "//site-malicioso.com").session(sessao))
                .andExpect(redirectedUrl("/login"));

        assertThat(sessao.getAttribute(LoginSuccessHandler.ATRIBUTO_RETORNO)).isNull();
    }

    @Test
    void depoisDoLoginVoltaParaAPublicacao() throws Exception {
        userRepository.saveAndFlush(new User("Retorno", "retorno@teste.com",
                passwordEncoder.encode("senha1234"), Role.CIDADAO));
        MockHttpSession sessao = new MockHttpSession();

        mvc().perform(get("/acesso").param("retorno", "/denuncias/7").session(sessao));

        mvc().perform(post("/login").with(csrf()).session(sessao)
                        .param("username", "retorno@teste.com")
                        .param("password", "senha1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/denuncias/7"));
    }
}