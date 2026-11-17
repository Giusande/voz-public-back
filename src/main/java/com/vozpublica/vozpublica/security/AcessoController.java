package com.vozpublica.vozpublica.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AcessoController {

    @GetMapping("/acesso")
    public String acesso(@RequestParam(required = false) String retorno,
                         @RequestParam(defaultValue = "login") String destino,
                         HttpSession sessao) {
        if (retorno != null && LoginSuccessHandler.RETORNO_VALIDO.matcher(retorno).matches()) {
            sessao.setAttribute(LoginSuccessHandler.ATRIBUTO_RETORNO, retorno);
        }
        return "redirect:" + ("cadastro".equals(destino) ? "/cadastro" : "/login");
    }
}