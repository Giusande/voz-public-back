package com.vozpublica.vozpublica.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ContaProps {

    private final boolean verificacaoObrigatoria;

    public ContaProps(@Value("${app.email.verificacao-obrigatoria:true}") boolean verificacaoObrigatoria) {
        this.verificacaoObrigatoria = verificacaoObrigatoria;
    }

    public boolean isVerificacaoObrigatoria() {
        return verificacaoObrigatoria;
    }
}