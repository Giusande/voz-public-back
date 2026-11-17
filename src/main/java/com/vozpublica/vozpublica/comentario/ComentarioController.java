package com.vozpublica.vozpublica.comentario;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ComentarioController {

    private final ComentarioService comentarioService;

    public ComentarioController(ComentarioService comentarioService) {
        this.comentarioService = comentarioService;
    }

    @PostMapping("/denuncias/{id:\\d+}/comentarios")
    public String comentar(@PathVariable Long id,
                           @Valid @ModelAttribute("comentarioForm") ComentarioForm form,
                           BindingResult result, Authentication authentication,
                           RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("erroComentario",
                    result.getFieldError("conteudo").getDefaultMessage());
        } else {
            comentarioService.adicionar(id, authentication.getName(), form.getConteudo());
        }
        return "redirect:/denuncias/" + id + "#comentarios";
    }
}