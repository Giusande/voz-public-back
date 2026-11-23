package com.vozpublica.vozpublica.security;

import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class SessaoAtualizadaFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public SessaoAtualizadaFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return caminho.equals("/login") || caminho.equals("/logout");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            Optional<User> usuario = userRepository.findByEmail(auth.getName().trim().toLowerCase());
            HttpSession sessao = request.getSession(false);
            if (usuario.isPresent() && desatualizada(sessao, auth, usuario.get())) {
                SecurityContextHolder.clearContext();
                if (sessao != null) {
                    sessao.invalidate();
                }
                response.sendRedirect(request.getContextPath() + "/login?encerrada");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean desatualizada(HttpSession sessao, Authentication auth, User usuario) {
        if (!usuario.isEnabled()) {
            return true;
        }
        Set<String> perfis = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .collect(Collectors.toSet());
        if (!perfis.equals(Set.of("ROLE_" + usuario.getRole().name()))) {
            return true;
        }
        Object versao = sessao == null ? null : sessao.getAttribute(LoginSuccessHandler.ATRIBUTO_VERSAO);
        return versao instanceof Integer v && v != usuario.getVersaoCredencial();
    }
}