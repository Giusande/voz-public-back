package com.vozpublica.vozpublica.user;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class RegisterController {

    private final UserService userService;

    public RegisterController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/cadastro")
    public String form(Model model) {
        model.addAttribute("registerForm", new RegisterForm());
        return "register";
    }

    @PostMapping("/cadastro")
    public String submit(@Valid @ModelAttribute("registerForm") RegisterForm form,
                         BindingResult result) {

        if (!result.hasFieldErrors("password") && !result.hasFieldErrors("confirmPassword")
                && !form.getPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "mismatch", "As senhas não coincidem.");
        }

        if (result.hasErrors()) {
            return "register";
        }

        try {
            userService.register(form);
        } catch (EmailAlreadyUsedException e) {
            result.rejectValue("email", "duplicate", e.getMessage());
            return "register";
        }

        return "redirect:/login?registered";
    }
}