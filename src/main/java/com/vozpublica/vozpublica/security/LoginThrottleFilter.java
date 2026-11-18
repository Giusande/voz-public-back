package com.vozpublica.vozpublica.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Recusa a tentativa de login, mesmo com a senha certa, enquanto o par e-mail+IP ou o IP estiver bloqueado. */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private final LimitadorTentativas limitador;

    public LoginThrottleFilter(LimitadorTentativas limitador) {
        this.limitador = limitador;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return !("POST".equalsIgnoreCase(request.getMethod()) && caminho.equals("/login"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        if (limitador.bloqueado(ChavesLogin.ip(ip))
                || limitador.bloqueado(ChavesLogin.par(ChavesLogin.emailDe(request), ip))) {
            response.sendRedirect(request.getContextPath() + "/login?bloqueado");
            return;
        }
        chain.doFilter(request, response);
    }
}