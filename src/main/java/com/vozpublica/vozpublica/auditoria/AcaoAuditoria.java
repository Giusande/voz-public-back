package com.vozpublica.vozpublica.auditoria;

public enum AcaoAuditoria {
    PRIORIDADE_OFICIAL_DEFINIDA("Prioridade oficial definida"),
    UNIDADE_CRIADA("Unidade criada"),
    UNIDADE_DESATIVADA("Unidade desativada"),
    UNIDADE_REATIVADA("Unidade reativada"),
    PRAZO_CATEGORIA_ALTERADO("Prazo de categoria alterado"),
    PAPEL_ALTERADO("Perfil de usuário alterado"),
    USUARIO_SUSPENSO("Usuário suspenso"),
    USUARIO_REATIVADO("Usuário reativado"),
    CONTEUDO_OCULTADO("Conteúdo ocultado"),
    CONTEUDO_RESTAURADO("Conteúdo restaurado"),
    SINALIZACAO_DESCARTADA("Sinalização descartada");

    private final String rotulo;

    AcaoAuditoria(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }
}