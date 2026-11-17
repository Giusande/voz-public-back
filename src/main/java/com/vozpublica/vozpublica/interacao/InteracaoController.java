package com.vozpublica.vozpublica.interacao;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class InteracaoController {

    private final InteracaoService interacaoService;

    public InteracaoController(InteracaoService interacaoService) {
        this.interacaoService = interacaoService;
    }

    @PostMapping("/denuncias/{id:\\d+}/curtir")
    public String curtir(@PathVariable Long id, Authentication authentication) {
        interacaoService.alternarCurtida(id, authentication.getName());
        return "redirect:/denuncias/" + id;
    }

    @PostMapping("/denuncias/{id:\\d+}/incentivar")
    public String incentivar(@PathVariable Long id, Authentication authentication) {
        interacaoService.incentivar(id, authentication.getName());
        return "redirect:/denuncias/" + id;
    }
}