package com.vozpublica.vozpublica.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LimitadorTentativas limitador;

    public LoginFailureHandler(LimitadorTentativas limitador) {
        super("/login?error");
        this.limitador = limitador;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String ip = request.getRemoteAddr();
        limitador.registrarFalha(ChavesLogin.par(ChavesLogin.emailDe(request), ip),
                ChavesLogin.LIMITE_PAR, ChavesLogin.JANELA, ChavesLogin.BLOQUEIO);
        limitador.registrarFalha(ChavesLogin.ip(ip),
                ChavesLogin.LIMITE_IP, ChavesLogin.JANELA, ChavesLogin.BLOQUEIO);
        super.onAuthenticationFailure(request, response, exception);
    }
}