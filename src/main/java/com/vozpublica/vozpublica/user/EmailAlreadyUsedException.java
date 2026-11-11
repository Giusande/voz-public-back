package com.vozpublica.vozpublica.user;

public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException() {
        super("E-mail já cadastrado.");
    }
}
