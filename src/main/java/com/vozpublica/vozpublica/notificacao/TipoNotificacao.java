package com.vozpublica.vozpublica.notificacao;

public enum TipoNotificacao {
    SITUACAO("Situação", Destino.PUBLICA),
    REABERTURA("Reabertura", Destino.PUBLICA),
    COMENTARIO("Comentário", Destino.PUBLICA),
    ADMINISTRATIVA("Atualização", Destino.PUBLICA),
    AGRUPAMENTO("Agrupamento", Destino.MINHAS),
    MODERACAO("Moderação", Destino.MINHAS),
    INFORMACAO("Informação solicitada", Destino.MINHAS),
    RESPOSTA_INFORMACAO("Resposta recebida", Destino.GESTAO);

    private enum Destino { PUBLICA, MINHAS, GESTAO }

    private final String rotulo;
    private final Destino destino;

    TipoNotificacao(String rotulo, Destino destino) {
        this.rotulo = rotulo;
        this.destino = destino;
    }

    public String getRotulo() { return rotulo; }

    public String caminho(Long denunciaId) {
        return switch (destino) {
            case MINHAS -> "/minhas-denuncias";
            case GESTAO -> denunciaId == null ? "/gestao/dashboard" : "/gestao/denuncias/" + denunciaId;
            case PUBLICA -> denunciaId == null ? "/denuncias" : "/denuncias/" + denunciaId;
        };
    }
}