package com.vozpublica.vozpublica.administracao;

import com.vozpublica.vozpublica.unidade.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/unidades")
public class UnidadeAdminController {

    private final UnidadeRepository unidadeRepository;
    private final UnidadeAdminService unidadeService;

    public UnidadeAdminController(UnidadeRepository unidadeRepository, UnidadeAdminService unidadeService) {
        this.unidadeRepository = unidadeRepository;
        this.unidadeService = unidadeService;
    }

    @GetMapping
    public String listar(Model model) {
        List<Unidade> todas = unidadeRepository.findAllByOrderByNomeAsc();
        model.addAttribute("unidades", todas);
        model.addAttribute("pais", todas.stream()
                .filter(u -> u.isAtiva() && u.getTipo() != TipoUnidade.EQUIPE).toList());
        model.addAttribute("tipos", TipoUnidade.values());
        return "admin-unidades";
    }

    @PostMapping
    public String criar(@RequestParam(required = false) String nome,
                        @RequestParam(required = false) TipoUnidade tipo,
                        @RequestParam(required = false) Long paiId,
                        RedirectAttributes redirect) {
        try {
            unidadeService.criar(nome, tipo, paiId);
            redirect.addFlashAttribute("mensagem", "Unidade cadastrada.");
        } catch (UnidadeInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/unidades";
    }

    @PostMapping("/{id:\\d+}/ativa")
    public String alternar(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            unidadeService.alternarAtiva(id);
            redirect.addFlashAttribute("mensagem", "Situação da unidade atualizada.");
        } catch (UnidadeInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/unidades";
    }
}