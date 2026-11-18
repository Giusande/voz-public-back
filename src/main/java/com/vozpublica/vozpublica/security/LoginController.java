package com.vozpublica.vozpublica.security;

import com.vozpublica.vozpublica.user.ContaProps;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    private final ContaProps props;

    public LoginController(ContaProps props) {
        this.props = props;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("verificacaoObrigatoria", props.isVerificacaoObrigatoria());
        return "login";
    }
}