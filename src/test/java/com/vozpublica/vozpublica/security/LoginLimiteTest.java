package com.vozpublica.vozpublica.security;

import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class LoginLimiteTest {

    private static final String SENHA = "senhaboa123";

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static String ipNovo() {
        var r = ThreadLocalRandom.current();
        return "10." + r.nextInt(1, 250) + "." + r.nextInt(1, 250) + "." + r.nextInt(1, 250);
    }

    private static RequestPostProcessor de(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private User usuario() {
        return userRepository.saveAndFlush(new User("Teste", "lim." + UUID.randomUUID() + "@teste.com",
                passwordEncoder.encode(SENHA), Role.CIDADAO));
    }

    private ResultActions login(String email, String senha, String ip) throws Exception {
        return mvc().perform(post("/login").with(csrf()).with(de(ip))
                .param("username", email).param("password", senha));
    }

    private void errar(String email, String ip, int vezes) throws Exception {
        for (int i = 0; i < vezes; i++) {
            login(email, "errada" + i, ip).andExpect(redirectedUrl("/login?error"));
        }
    }

    @Test
    void cincoErrosBloqueiamOParEmailEIpMesmoComASenhaCerta() throws Exception {
        User u = usuario();
        String ip = ipNovo();

        errar(u.getEmail(), ip, 5);

        login(u.getEmail(), SENHA, ip).andExpect(redirectedUrl("/login?bloqueado"));
    }

    @Test
    void oBloqueioNaoAtingeOutroIpNemOutraContaNoMesmoIp() throws Exception {
        User alvo = usuario();
        User outra = usuario();
        String ipAtacante = ipNovo();
        errar(alvo.getEmail(), ipAtacante, 5);

        login(alvo.getEmail(), SENHA, ipNovo()).andExpect(redirectedUrl("/")); // o dono, de outro IP
        login(outra.getEmail(), SENHA, ipAtacante).andExpect(redirectedUrl("/"));
    }

    @Test
    void loginComSucessoZeraAContagemDoPar() throws Exception {
        User u = usuario();
        String ip = ipNovo();

        errar(u.getEmail(), ip, 4);
        login(u.getEmail(), SENHA, ip).andExpect(redirectedUrl("/"));
        errar(u.getEmail(), ip, 4);

        login(u.getEmail(), SENHA, ip).andExpect(redirectedUrl("/"));
    }

    @Test
    void vinteErrosDeUmMesmoIpBloqueiamOIpInteiro() throws Exception {
        User u = usuario();
        String ip = ipNovo();

        for (int i = 0; i < 20; i++) {
            login("inexistente." + i + "." + UUID.randomUUID() + "@teste.com", "x", ip)
                    .andExpect(redirectedUrl("/login?error"));
        }

        login(u.getEmail(), SENHA, ip).andExpect(redirectedUrl("/login?bloqueado"));
    }

    @Test
    void paginaDeLoginExplicaOBloqueioEOferecePasswordRecovery() throws Exception {
        String html = mvc().perform(get("/login").param("bloqueado", ""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Muitas tentativas").contains("/recuperar-senha");
    }
}