package com.vozpublica.vozpublica.confirmacao;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ConfirmacaoController {

    private final ConfirmacaoService service;

    public ConfirmacaoController(ConfirmacaoService service) {
        this.service = service;
    }

    @PostMapping("/denuncias/{id:\\d+}/confirmar")
    public String confirmar(@PathVariable Long id,
                            @RequestParam(required = false) TipoConfirmacao tipo,
                            Authentication authentication, RedirectAttributes redirect) {
        try {
            service.registrar(id, tipo, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Seu relato foi registrado. Obrigado!");
        } catch (ConfirmacaoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/denuncias/" + id + "#confirmacao";
    }

    @PostMapping("/denuncias/{id:\\d+}/confirmar/remover")
    public String remover(@PathVariable Long id, Authentication authentication,
                          RedirectAttributes redirect) {
        service.remover(id, authentication.getName());
        redirect.addFlashAttribute("mensagem", "Seu relato foi retirado.");
        return "redirect:/denuncias/" + id + "#confirmacao";
    }
}