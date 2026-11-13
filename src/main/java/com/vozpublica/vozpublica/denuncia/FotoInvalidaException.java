package com.vozpublica.vozpublica.denuncia;

public class FotoInvalidaException extends RuntimeException {
    public FotoInvalidaException(String mensagem) {
        super(mensagem);
    }
}