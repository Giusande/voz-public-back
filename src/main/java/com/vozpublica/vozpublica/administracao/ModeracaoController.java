package com.vozpublica.vozpublica.administracao;

import com.vozpublica.vozpublica.moderacao.ModeracaoException;
import com.vozpublica.vozpublica.moderacao.ModeracaoService;
import com.vozpublica.vozpublica.moderacao.TipoConteudo;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/moderacao")
public class ModeracaoController {

    private final ModeracaoService moderacaoService;

    public ModeracaoController(ModeracaoService moderacaoService) {
        this.moderacaoService = moderacaoService;
    }

    @GetMapping
    public String fila(@RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("page", moderacaoService.fila(pagina));
        return "admin-moderacao";
    }

    @PostMapping("/sinalizacoes/{id:\\d+}/ocultar")
    public String ocultarPelaSinalizacao(@PathVariable Long id,
                                         @RequestParam(required = false) String justificativa,
                                         Authentication authentication, RedirectAttributes redirect) {
        try {
            moderacaoService.ocultarPelaSinalizacao(id, justificativa, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Conteúdo ocultado e sinalizações encerradas.");
        } catch (ModeracaoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/moderacao";
    }

    @PostMapping("/sinalizacoes/{id:\\d+}/descartar")
    public String descartar(@PathVariable Long id,
                            @RequestParam(required = false) String justificativa,
                            Authentication authentication, RedirectAttributes redirect) {
        try {
            moderacaoService.descartar(id, justificativa, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Sinalização descartada.");
        } catch (ModeracaoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/moderacao";
    }

    @PostMapping("/conteudo/{tipo}/{id:\\d+}/ocultar")
    public String ocultar(@PathVariable TipoConteudo tipo, @PathVariable Long id,
                          @RequestParam(required = false) String justificativa,
                          @RequestParam(required = false) Long denunciaId,
                          Authentication authentication, RedirectAttributes redirect) {
        try {
            moderacaoService.ocultar(tipo, id, justificativa, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Conteúdo ocultado.");
        } catch (ModeracaoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return voltar(denunciaId);
    }

    @PostMapping("/conteudo/{tipo}/{id:\\d+}/restaurar")
    public String restaurar(@PathVariable TipoConteudo tipo, @PathVariable Long id,
                            @RequestParam(required = false) String justificativa,
                            @RequestParam(required = false) Long denunciaId,
                            Authentication authentication, RedirectAttributes redirect) {
        try {
            moderacaoService.restaurar(tipo, id, justificativa, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Conteúdo restaurado.");
        } catch (ModeracaoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return voltar(denunciaId);
    }

    /** O destino é montado só com um número (Long): não há como redirecionar para outro site. */
    private static String voltar(Long denunciaId) {
        return denunciaId != null ? "redirect:/denuncias/" + denunciaId : "redirect:/admin/moderacao";
    }
}