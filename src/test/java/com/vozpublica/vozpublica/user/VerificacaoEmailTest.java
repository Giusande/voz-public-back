package com.vozpublica.vozpublica.user;

import com.vozpublica.vozpublica.email.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.email.verificacao-obrigatoria=true")
@Transactional
class VerificacaoEmailTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenContaService tokenService;
    @MockitoBean private EmailService emailService;

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

    private ResultActions cadastrar(String email, String ip) throws Exception {
        return mvc().perform(post("/cadastro").with(csrf()).with(de(ip))
                .param("name", "Fulana").param("email", email)
                .param("password", "senhaboa123").param("confirmPassword", "senhaboa123"));
    }

    private ResultActions login(String email, String ip) throws Exception {
        return mvc().perform(post("/login").with(csrf()).with(de(ip))
                .param("username", email).param("password", "senhaboa123"));
    }

    private List<String> tokensEnviados(String email, int vezes) {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(vezes)).enviarVerificacao(eq(email), anyString(), token.capture());
        return token.getAllValues();
    }

    private String novoEmail() {
        return "ver." + UUID.randomUUID() + "@teste.com";
    }

    @Test
    void cadastroCriaContaPendenteEnviaOLinkEImpedeOLogin() throws Exception {
        String email = novoEmail();
        String ip = ipNovo();

        cadastrar(email, ip).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?registered"));

        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerificado()).isFalse();
        assertThat(tokensEnviados(email, 1).get(0)).hasSize(43);
        login(email, ip).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void abrirOLinkNaoConfirmaEOBotaoConfirma() throws Exception {
        String email = novoEmail();
        String ip = ipNovo();
        cadastrar(email, ip);
        String token = tokensEnviados(email, 1).get(0);

        mvc().perform(get("/verificar-email").param("token", token))
                .andExpect(status().isOk()).andExpect(model().attribute("valido", true));
        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerificado()).isFalse();

        mvc().perform(post("/verificar-email").with(csrf()).with(de(ip)).param("token", token))
                .andExpect(redirectedUrl("/login?verificado"));

        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerificado()).isTrue();
        login(email, ip).andExpect(redirectedUrl("/"));
    }

    @Test
    void tokenDeVerificacaoNaoServePraRedefinirSenhaENemOContrario() throws Exception {
        String email = novoEmail();
        cadastrar(email, ipNovo());
        String token = tokensEnviados(email, 1).get(0);

        mvc().perform(get("/redefinir-senha").param("token", token))
                .andExpect(model().attribute("tokenValido", false));

        User u = userRepository.findByEmail(email).orElseThrow();
        String redefinicao = tokenService.emitir(u.getId(), TipoToken.REDEFINICAO_SENHA);
        mvc().perform(post("/verificar-email").with(csrf()).param("token", redefinicao))
                .andExpect(status().isOk()).andExpect(model().attribute("valido", false));
    }

    @Test
    void reenviarGeraLinkNovoEInvalidaOAntigo() throws Exception {
        String email = novoEmail();
        String ip = ipNovo();
        cadastrar(email, ip);

        mvc().perform(post("/verificar-email/reenviar").with(csrf()).with(de(ip)).param("email", email))
                .andExpect(redirectedUrl("/verificar-email/reenviar?enviado"));

        List<String> tokens = tokensEnviados(email, 2);
        assertThat(tokens.get(1)).isNotEqualTo(tokens.get(0));

        mvc().perform(post("/verificar-email").with(csrf()).param("token", tokens.get(0)))
                .andExpect(status().isOk()).andExpect(model().attribute("valido", false));
        mvc().perform(post("/verificar-email").with(csrf()).param("token", tokens.get(1)))
                .andExpect(redirectedUrl("/login?verificado"));
    }

    @Test
    void reenvioNaoRevelaSeAContaExisteENaoEnviaParaContaJaVerificada() throws Exception {
        User verificada = userRepository.saveAndFlush(new User("Antiga", novoEmail(),
                passwordEncoder.encode("senhaboa123"), Role.CIDADAO));
        String ip = ipNovo();

        mvc().perform(post("/verificar-email/reenviar").with(csrf()).with(de(ip))
                        .param("email", "naoexiste." + UUID.randomUUID() + "@teste.com"))
                .andExpect(redirectedUrl("/verificar-email/reenviar?enviado"));
        mvc().perform(post("/verificar-email/reenviar").with(csrf()).with(de(ip))
                        .param("email", verificada.getEmail()))
                .andExpect(redirectedUrl("/verificar-email/reenviar?enviado"));

        verify(emailService, never()).enviarVerificacao(anyString(), anyString(), anyString());
    }

    @Test
    void contaAntigaJaVerificadaEntraNormalmente() throws Exception {
        User antiga = userRepository.saveAndFlush(new User("Antiga", novoEmail(),
                passwordEncoder.encode("senhaboa123"), Role.CIDADAO));

        login(antiga.getEmail(), ipNovo()).andExpect(redirectedUrl("/"));
    }
}