package com.vozpublica.vozpublica.encaminhamento;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AtrasadasController {

    private final AtrasoService atrasoService;

    public AtrasadasController(AtrasoService atrasoService) {
        this.atrasoService = atrasoService;
    }

    @GetMapping("/gestao/atrasadas")
    public String listar(@RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("page", atrasoService.listar(pagina));
        return "gestao-atrasadas";
    }
}