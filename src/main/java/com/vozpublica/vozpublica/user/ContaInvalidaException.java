package com.vozpublica.vozpublica.user;

public class ContaInvalidaException extends RuntimeException {
    public ContaInvalidaException(String mensagem) {
        super(mensagem);
    }
}