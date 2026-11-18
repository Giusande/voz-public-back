package com.vozpublica.vozpublica.user;

import com.vozpublica.vozpublica.email.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ContaRecuperacaoController {

    private final ContaRecuperacaoService servico;
    private final EmailService emailService;

    public ContaRecuperacaoController(ContaRecuperacaoService servico, EmailService emailService) {
        this.servico = servico;
        this.emailService = emailService;
    }

    // ---- esqueci minha senha ----

    @GetMapping("/recuperar-senha")
    public String recuperarForm() {
        return "recuperar-senha";
    }

    /** Resposta sempre igual, exista a conta ou não. O e-mail só sai depois que a transação terminou. */
    @PostMapping("/recuperar-senha")
    public String recuperar(@RequestParam(required = false) String email, HttpServletRequest request) {
        servico.solicitarRecuperacao(email, request.getRemoteAddr())
                .ifPresent(e -> emailService.enviarRecuperacao(e.destino(), e.nome(), e.token()));
        return "redirect:/recuperar-senha?enviado";
    }

    // ---- nova senha ----

    @GetMapping("/redefinir-senha")
    public String redefinirForm(@RequestParam(required = false) String token, Model model) {
        boolean valido = servico.tokenRedefinicaoValido(token);
        RedefinirSenhaForm form = new RedefinirSenhaForm();
        form.setToken(valido ? token : null);
        model.addAttribute("form", form);
        model.addAttribute("tokenValido", valido);
        return "redefinir-senha";
    }

    @PostMapping("/redefinir-senha")
    public String redefinir(@Valid @ModelAttribute("form") RedefinirSenhaForm form, BindingResult result,
                            HttpServletRequest request, Model model) {
        if (!result.hasFieldErrors("password") && !result.hasFieldErrors("confirmPassword")
                && !form.getPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "mismatch", "As senhas não coincidem.");
        }
        model.addAttribute("tokenValido", true);
        if (result.hasErrors()) {
            return "redefinir-senha"; // o token não foi gasto
        }
        if (!servico.redefinirSenha(form.getToken(), form.getPassword(), request.getRemoteAddr())) {
            model.addAttribute("tokenValido", false);
            return "redefinir-senha";
        }
        return "redirect:/login?senhaRedefinida";
    }

    // ---- verificação de e-mail ----

    /** Abrir o link não confirma nada: a confirmação é o POST do botão. */
    @GetMapping("/verificar-email")
    public String verificarPagina(@RequestParam(required = false) String token, Model model) {
        boolean valido = servico.tokenVerificacaoValido(token);
        model.addAttribute("valido", valido);
        model.addAttribute("token", valido ? token : "");
        return "verificar-email";
    }

    @PostMapping("/verificar-email")
    public String verificar(@RequestParam(required = false) String token, Model model) {
        if (servico.verificarEmail(token)) {
            return "redirect:/login?verificado";
        }
        model.addAttribute("valido", false);
        model.addAttribute("token", "");
        return "verificar-email";
    }

    @GetMapping("/verificar-email/reenviar")
    public String reenviarForm() {
        return "reenviar-verificacao";
    }

    @PostMapping("/verificar-email/reenviar")
    public String reenviar(@RequestParam(required = false) String email, HttpServletRequest request) {
        servico.reenviarVerificacao(email, request.getRemoteAddr())
                .ifPresent(e -> emailService.enviarVerificacao(e.destino(), e.nome(), e.token()));
        return "redirect:/verificar-email/reenviar?enviado";
    }
}