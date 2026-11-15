package com.vozpublica.vozpublica.moderacao;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SinalizacaoController {

    private final SinalizacaoService sinalizacaoService;

    public SinalizacaoController(SinalizacaoService sinalizacaoService) {
        this.sinalizacaoService = sinalizacaoService;
    }

    @PostMapping("/denuncias/{id:\\d+}/sinalizar")
    public String sinalizarDenuncia(@PathVariable Long id,
                                    @RequestParam(required = false) MotivoSinalizacao motivo,
                                    @RequestParam(required = false) String detalhe,
                                    Authentication authentication, RedirectAttributes redirect) {
        try {
            sinalizacaoService.sinalizarDenuncia(id, motivo, detalhe, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Sinalização enviada à moderação. Obrigado.");
        } catch (ModeracaoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/denuncias/" + id;
    }

    @PostMapping("/denuncias/{id:\\d+}/comentarios/{comentarioId:\\d+}/sinalizar")
    public String sinalizarComentario(@PathVariable Long id, @PathVariable Long comentarioId,
                                      @RequestParam(required = false) MotivoSinalizacao motivo,
                                      @RequestParam(required = false) String detalhe,
                                      Authentication authentication, RedirectAttributes redirect) {
        try {
            sinalizacaoService.sinalizarComentario(id, comentarioId, motivo, detalhe,
                    authentication.getName());
            redirect.addFlashAttribute("mensagem", "Sinalização enviada à moderação. Obrigado.");
        } catch (ModeracaoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/denuncias/" + id + "#comentarios";
    }
}