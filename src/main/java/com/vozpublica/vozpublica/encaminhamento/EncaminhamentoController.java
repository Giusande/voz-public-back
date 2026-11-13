package com.vozpublica.vozpublica.encaminhamento;

import com.vozpublica.vozpublica.denuncia.GestaoInvalidaException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class EncaminhamentoController {

    private final EncaminhamentoService encaminhamentoService;

    public EncaminhamentoController(EncaminhamentoService encaminhamentoService) {
        this.encaminhamentoService = encaminhamentoService;
    }

    @PostMapping("/gestao/denuncias/{id:\\d+}/encaminhar")
    public String encaminhar(@PathVariable Long id,
                             @RequestParam(required = false) Long unidadeId,
                             @RequestParam(required = false) Long servidorId,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate prazo,
                             @RequestParam(required = false) String observacao,
                             Authentication authentication,
                             RedirectAttributes redirect) {
        try {
            encaminhamentoService.encaminhar(id, unidadeId, servidorId, prazo, observacao,
                    authentication.getName());
            redirect.addFlashAttribute("mensagem", "Encaminhamento registrado.");
        } catch (GestaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/gestao/denuncias/" + id;
    }
}