package com.vozpublica.vozpublica.shared;

import com.vozpublica.vozpublica.notificacao.NotificacaoService;
import com.vozpublica.vozpublica.security.Perfis;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.function.Supplier;

/** Informa a todas as páginas quais grupos de links o menu deve mostrar. */
@ControllerAdvice
public class MenuAdvice {

    /** Consulta o banco só quando o template pede o total, e uma única vez. */
    public static final class NaoLidas {
        private final Supplier<Long> origem;
        private Long cache;

        NaoLidas(Supplier<Long> origem) {
            this.origem = origem;
        }

        public long getTotal() {
            if (cache == null) {
                cache = origem.get();
            }
            return cache;
        }
    }

    private final NotificacaoService notificacaoService;

    public MenuAdvice(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @ModelAttribute
    public void menu(Authentication authentication, Model model) {
        boolean logado = authentication != null;
        model.addAttribute("navLogado", logado);
        model.addAttribute("navEquipe", Perfis.ehEquipe(authentication));
        model.addAttribute("navAdmin", Perfis.ehAdministrador(authentication));
        if (logado) {
            String email = authentication.getName();
            model.addAttribute("navNaoLidas", new NaoLidas(() -> notificacaoService.naoLidas(email)));
        }
    }
}