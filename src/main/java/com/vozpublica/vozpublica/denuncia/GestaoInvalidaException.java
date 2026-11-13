package com.vozpublica.vozpublica.denuncia;

public class GestaoInvalidaException extends RuntimeException {
    public GestaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}