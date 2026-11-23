package com.vozpublica.vozpublica.relatorio;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class TransparenciaController {

    private final TransparenciaService service;

    public TransparenciaController(TransparenciaService service) {
        this.service = service;
    }

    @GetMapping("/transparencia")
    public String pagina(@ModelAttribute("filtro") FiltroRelatorio filtro,
                         BindingResult binding,
                         Model model) {
        model.addAttribute("painel", service.painel(filtro.getDe(), filtro.getAte()));
        return "transparencia";
    }
}