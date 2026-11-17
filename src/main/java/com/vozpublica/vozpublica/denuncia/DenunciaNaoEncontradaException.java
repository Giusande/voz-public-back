package com.vozpublica.vozpublica.denuncia;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DenunciaNaoEncontradaException extends RuntimeException {
    public DenunciaNaoEncontradaException() {
        super("Ocorrência não encontrada.");
    }
}