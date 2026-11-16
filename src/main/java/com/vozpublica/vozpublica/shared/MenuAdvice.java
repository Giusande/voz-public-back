package com.vozpublica.vozpublica.shared;

import com.vozpublica.vozpublica.security.Perfis;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Informa a todas as páginas quais grupos de links o menu deve mostrar. */
@ControllerAdvice
public class MenuAdvice {

    @ModelAttribute
    public void menu(Authentication authentication, Model model) {
        model.addAttribute("navLogado", authentication != null);
        model.addAttribute("navEquipe", Perfis.ehEquipe(authentication));
        model.addAttribute("navAdmin", Perfis.ehAdministrador(authentication));
    }
}