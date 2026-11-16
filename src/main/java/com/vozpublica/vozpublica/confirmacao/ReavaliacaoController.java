package com.vozpublica.vozpublica.confirmacao;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ReavaliacaoController {

    private final ReavaliacaoService service;

    public ReavaliacaoController(ReavaliacaoService service) {
        this.service = service;
    }

    @GetMapping("/gestao/reavaliacao")
    public String listar(Model model) {
        model.addAttribute("itens", service.listar());
        model.addAttribute("minimo", ReavaliacaoService.MINIMO);
        return "gestao-reavaliacao";
    }
}