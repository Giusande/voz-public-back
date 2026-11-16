package com.vozpublica.vozpublica.denuncia;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MinhasDenunciasController {

    private final MinhasDenunciasService service;

    public MinhasDenunciasController(MinhasDenunciasService service) {
        this.service = service;
    }

    @GetMapping("/minhas-denuncias")
    public String listar(@RequestParam(defaultValue = "0") int pagina,
                         Authentication authentication, Model model) {
        model.addAttribute("v", service.listar(authentication.getName(), pagina));
        return "minhas-denuncias";
    }
}