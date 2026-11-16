package com.vozpublica.vozpublica.notificacao;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class NotificacaoController {

    private final NotificacaoService service;

    public NotificacaoController(NotificacaoService service) {
        this.service = service;
    }

    @GetMapping("/notificacoes")
    public String listar(@RequestParam(defaultValue = "0") int pagina,
                         Authentication authentication, Model model) {
        model.addAttribute("page", service.listar(authentication.getName(), pagina));
        return "notificacoes";
    }

    @PostMapping("/notificacoes/{id:\\d+}/lida")
    public String lida(@PathVariable Long id, Authentication authentication) {
        service.marcarLida(authentication.getName(), id);
        return "redirect:/notificacoes";
    }

    @PostMapping("/notificacoes/lidas")
    public String todasLidas(Authentication authentication) {
        service.marcarTodas(authentication.getName());
        return "redirect:/notificacoes";
    }
}