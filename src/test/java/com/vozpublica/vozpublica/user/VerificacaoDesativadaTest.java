package com.vozpublica.vozpublica.user;

import com.vozpublica.vozpublica.email.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest(properties = "app.email.verificacao-obrigatoria=false")
@Transactional
class VerificacaoDesativadaTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @MockitoBean private EmailService emailService;

    @Test
    void comAVerificacaoDesligadaAContaJaNasceVerificadaESemEmail() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String email = "semver." + UUID.randomUUID() + "@teste.com";

        mvc.perform(post("/cadastro").with(csrf()).with(r -> { r.setRemoteAddr("10.77.1.1"); return r; })
                .param("name", "Fulana").param("email", email)
                .param("password", "senhaboa123").param("confirmPassword", "senhaboa123"));

        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerificado()).isTrue();
        verify(emailService, never()).enviarVerificacao(anyString(), anyString(), anyString());

        mvc.perform(post("/login").with(csrf()).with(r -> { r.setRemoteAddr("10.77.1.1"); return r; })
                        .param("username", email).param("password", "senhaboa123"))
                .andExpect(redirectedUrl("/"));
    }
}