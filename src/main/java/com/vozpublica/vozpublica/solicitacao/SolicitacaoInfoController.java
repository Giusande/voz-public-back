package com.vozpublica.vozpublica.solicitacao;

import com.vozpublica.vozpublica.denuncia.GestaoInvalidaException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SolicitacaoInfoController {

    private final SolicitacaoInfoService service;

    public SolicitacaoInfoController(SolicitacaoInfoService service) {
        this.service = service;
    }

    @PostMapping("/gestao/denuncias/{id:\\d+}/solicitar-info")
    public String solicitar(@PathVariable Long id, @RequestParam(required = false) String mensagem,
                            Authentication authentication, RedirectAttributes redirect) {
        try {
            service.solicitar(id, mensagem, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Solicitação enviada ao autor.");
        } catch (GestaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/gestao/denuncias/" + id;
    }

    @PostMapping("/minhas-denuncias/{id:\\d+}/responder")
    public String responder(@PathVariable Long id, @RequestParam(required = false) String resposta,
                            Authentication authentication, RedirectAttributes redirect) {
        try {
            service.responder(id, resposta, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Resposta enviada à equipe.");
        } catch (GestaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/minhas-denuncias";
    }
}