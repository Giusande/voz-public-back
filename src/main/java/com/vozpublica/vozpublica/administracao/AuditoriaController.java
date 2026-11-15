package com.vozpublica.vozpublica.administracao;

import com.vozpublica.vozpublica.auditoria.AcaoAuditoria;
import com.vozpublica.vozpublica.auditoria.AuditoriaService;
import com.vozpublica.vozpublica.auditoria.FiltroAuditoria;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/admin/auditoria")
    public String listar(@ModelAttribute("filtro") FiltroAuditoria filtro,
                         BindingResult binding, // valores inválidos nos filtros são ignorados
                         @RequestParam(defaultValue = "0") int pagina,
                         Model model) {
        model.addAttribute("page", auditoriaService.listar(filtro, pagina));
        model.addAttribute("acoes", AcaoAuditoria.values());
        return "admin-auditoria";
    }
}