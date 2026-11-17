package com.vozpublica.vozpublica.administracao;

import com.vozpublica.vozpublica.user.ContaAdminService;
import com.vozpublica.vozpublica.user.ContaInvalidaException;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/usuarios")
public class ContaAdminController {

    private static final int TAMANHO_PAGINA = 20;

    private final UserRepository userRepository;
    private final ContaAdminService contaService;

    public ContaAdminController(UserRepository userRepository, ContaAdminService contaService) {
        this.userRepository = userRepository;
        this.contaService = contaService;
    }

    @GetMapping
    public String listar(@RequestParam(defaultValue = "") String busca,
                         @RequestParam(defaultValue = "0") int pagina,
                         Authentication authentication, Model model) {
        String termo = busca.trim();
        if (termo.length() > 100) termo = termo.substring(0, 100);

        Pageable pageable = PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA, Sort.by("name", "id"));
        Page<User> page = termo.isEmpty()
                ? userRepository.findAll(pageable)
                : userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(termo, termo, pageable);

        model.addAttribute("page", page);
        model.addAttribute("busca", termo);
        model.addAttribute("papeis", Role.values());
        model.addAttribute("emailLogado", authentication.getName());
        return "admin-usuarios";
    }

    @PostMapping("/{id:\\d+}/papel")
    public String alterarPapel(@PathVariable Long id,
                               @RequestParam(required = false) Role papel,
                               @RequestParam(required = false) String justificativa,
                               Authentication authentication, RedirectAttributes redirect) {
        try {
            contaService.alterarPapel(id, papel, justificativa, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Perfil atualizado.");
        } catch (ContaInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/{id:\\d+}/suspender")
    public String suspender(@PathVariable Long id,
                            @RequestParam(required = false) String justificativa,
                            Authentication authentication, RedirectAttributes redirect) {
        try {
            contaService.suspender(id, justificativa, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Conta suspensa.");
        } catch (ContaInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/{id:\\d+}/reativar")
    public String reativar(@PathVariable Long id,
                           @RequestParam(required = false) String justificativa,
                           Authentication authentication, RedirectAttributes redirect) {
        try {
            contaService.reativar(id, justificativa, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Conta reativada.");
        } catch (ContaInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }
}