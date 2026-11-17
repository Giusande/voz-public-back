package com.vozpublica.vozpublica.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    @GetMapping("/perfil")
    public String meuPerfil(Authentication authentication) {
        return "redirect:/perfil/" + perfilService.idDe(authentication.getName());
    }

    @GetMapping("/perfil/{id:\\d+}")
    public String ver(@PathVariable Long id, @RequestParam(defaultValue = "0") int pagina,
                      Authentication authentication, Model model) {
        var perfil = perfilService.carregar(id,
                        authentication != null ? authentication.getName() : null, pagina)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("p", perfil);
        return "perfil";
    }

    @GetMapping("/perfil/configuracoes")
    public String configuracoes(Authentication authentication, Model model) {
        model.addAttribute("configForm", perfilService.formularioDe(authentication.getName()));
        model.addAttribute("meuId", perfilService.idDe(authentication.getName()));
        return "perfil-configuracoes";
    }

    @PostMapping("/perfil/configuracoes")
    public String salvar(@Valid @ModelAttribute("configForm") ConfigPerfilForm form, BindingResult result,
                         Authentication authentication, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("meuId", perfilService.idDe(authentication.getName()));
            return "perfil-configuracoes";
        }
        perfilService.atualizarPrivacidade(authentication.getName(), form);
        redirect.addFlashAttribute("mensagem", "Configurações salvas.");
        return "redirect:/perfil/configuracoes";
    }
}