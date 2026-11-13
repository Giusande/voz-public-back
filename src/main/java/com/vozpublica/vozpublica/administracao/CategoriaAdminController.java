package com.vozpublica.vozpublica.administracao;

import com.vozpublica.vozpublica.categoria.CategoriaAdminException;
import com.vozpublica.vozpublica.categoria.CategoriaAdminService;
import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/categorias")
public class CategoriaAdminController {

    private final CategoriaRepository categoriaRepository;
    private final CategoriaAdminService categoriaService;

    public CategoriaAdminController(CategoriaRepository categoriaRepository,
                                    CategoriaAdminService categoriaService) {
        this.categoriaRepository = categoriaRepository;
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("categorias", categoriaRepository.findByAtivaTrueOrderByNome());
        return "admin-categorias";
    }

    @PostMapping("/{id:\\d+}/prazo")
    public String alterarPrazo(@PathVariable Long id,
                               @RequestParam(required = false) Integer prazoDias,
                               RedirectAttributes redirect) {
        try {
            categoriaService.alterarPrazo(id, prazoDias);
            redirect.addFlashAttribute("mensagem", "Prazo atualizado.");
        } catch (CategoriaAdminException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/categorias";
    }
}