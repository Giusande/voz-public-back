package com.vozpublica.vozpublica.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ControllerAdvice
public class UploadExceptionHandler {

    private static final Pattern GESTAO = Pattern.compile("^/gestao/denuncias/(\\d+)/");

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tamanhoExcedido(HttpServletRequest request) {
        Matcher m = GESTAO.matcher(request.getRequestURI());
        if (m.find()) {
            return "redirect:/gestao/denuncias/" + m.group(1) + "?erroTamanho";
        }
        return "redirect:/denuncias/nova?erroTamanho";
    }
}