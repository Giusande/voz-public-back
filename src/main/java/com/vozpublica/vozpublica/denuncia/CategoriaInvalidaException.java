package com.vozpublica.vozpublica.denuncia;

public class CategoriaInvalidaException extends RuntimeException {
    public CategoriaInvalidaException() {
        super("Categoria inválida.");
    }
}