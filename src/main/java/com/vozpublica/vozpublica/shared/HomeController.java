package com.vozpublica.vozpublica.shared;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        model.addAttribute("mensagem", "Voz Pública está no ar!");
        model.addAttribute("usuario", authentication != null ? authentication.getName() : null);
        return "index";
    }
}