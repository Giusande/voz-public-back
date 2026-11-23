package com.vozpublica.vozpublica.encaminhamento;

public interface DenunciaAtrasada {
    Long getId();
    String getTitulo();
    String getBairro();
    String getCategoria();
    String getStatus();
    String getPrazo();
    String getUnidade();
}