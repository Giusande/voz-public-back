package com.vozpublica.vozpublica.security;

import com.vozpublica.vozpublica.user.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public static final String ATRIBUTO_RETORNO = "RETORNO_APOS_LOGIN";
    public static final String ATRIBUTO_VERSAO = "VERSAO_CREDENCIAL";
    public static final Pattern RETORNO_VALIDO = Pattern.compile("/denuncias/\\d+");

    private final LimitadorTentativas limitador;
    private final UserRepository userRepository;

    public LoginSuccessHandler(LimitadorTentativas limitador, UserRepository userRepository) {
        this.limitador = limitador;
        this.userRepository = userRepository;
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {
        limitador.limpar(ChavesLogin.par(ChavesLogin.emailDe(request), request.getRemoteAddr()));

        userRepository.findByEmail(authentication.getName().trim().toLowerCase()).ifPresent(u ->
                request.getSession().setAttribute(ATRIBUTO_VERSAO, u.getVersaoCredencial()));

        HttpSession sessao = request.getSession(false);
        if (sessao != null) {
            Object retorno = sessao.getAttribute(ATRIBUTO_RETORNO);
            sessao.removeAttribute(ATRIBUTO_RETORNO);
            if (retorno instanceof String url && RETORNO_VALIDO.matcher(url).matches()) {
                clearAuthenticationAttributes(request);
                getRedirectStrategy().sendRedirect(request, response, url);
                return;
            }
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}