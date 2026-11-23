package com.vozpublica.vozpublica.interacao;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.regex.Pattern;

@Controller
public class InteracaoController {

    private static final Pattern VOLTAR_VALIDO = Pattern.compile("/(denuncias)?(\\?[^\\s#\\\\{}]{0,500})?");

    private final InteracaoService interacaoService;

    public InteracaoController(InteracaoService interacaoService) {
        this.interacaoService = interacaoService;
    }

    @PostMapping("/denuncias/{id:\\d+}/curtir")
    public String curtir(@PathVariable Long id, @RequestParam(required = false) String voltar,
                         Authentication authentication) {
        interacaoService.alternarCurtida(id, authentication.getName());
        return destino(id, voltar);
    }

    @PostMapping("/denuncias/{id:\\d+}/incentivar")
    public String incentivar(@PathVariable Long id, @RequestParam(required = false) String voltar,
                             Authentication authentication) {
        interacaoService.incentivar(id, authentication.getName());
        return destino(id, voltar);
    }

    private static String destino(Long id, String voltar) {
        if (voltar != null && VOLTAR_VALIDO.matcher(voltar).matches()) {
            return "redirect:" + voltar + "#post-" + id;
        }
        return "redirect:/denuncias/" + id;
    }
}