package com.vozpublica.vozpublica.denuncia;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/gestao/denuncias/{id:\\d+}")
public class AgrupamentoController {

    private final AgrupamentoService agrupamentoService;

    public AgrupamentoController(AgrupamentoService agrupamentoService) {
        this.agrupamentoService = agrupamentoService;
    }

    @PostMapping("/agrupar")
    public String agrupar(@PathVariable Long id,
                          @RequestParam(required = false) Long principalId,
                          @RequestParam(required = false) String justificativa,
                          Authentication authentication, RedirectAttributes redirect) {
        try {
            agrupamentoService.agrupar(id, principalId, justificativa, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Ocorrência agrupada à principal.");
        } catch (GestaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/gestao/denuncias/" + id;
    }

    @PostMapping("/desagrupar")
    public String desagrupar(@PathVariable Long id,
                             @RequestParam(required = false) String justificativa,
                             Authentication authentication, RedirectAttributes redirect) {
        try {
            agrupamentoService.desagrupar(id, justificativa, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Ocorrência desagrupada.");
        } catch (GestaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/gestao/denuncias/" + id;
    }
}