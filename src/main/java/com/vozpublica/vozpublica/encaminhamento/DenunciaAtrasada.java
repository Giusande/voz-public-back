package com.vozpublica.vozpublica.encaminhamento;

/** Projeção da consulta nativa. O prazo vem como texto ISO (yyyy-MM-dd). */
public interface DenunciaAtrasada {
    Long getId();
    String getTitulo();
    String getBairro();
    String getCategoria();
    String getStatus();
    String getPrazo();
    String getUnidade();
}