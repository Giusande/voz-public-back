package com.vozpublica.vozpublica.encaminhamento;

import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.StatusDenuncia;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

public record Prazo(LocalDate data, boolean doEncaminhamento, boolean atrasada, long diasAtraso) {

    private static final Set<StatusDenuncia> ENCERRADAS =
            EnumSet.of(StatusDenuncia.RESOLVIDA, StatusDenuncia.INDEFERIDA);

    public static Prazo calcular(LocalDateTime criacao, int diasDaCategoria, LocalDate prazoEspecifico,
                                 StatusDenuncia status, LocalDate hoje) {
        LocalDate data = prazoEspecifico != null
                ? prazoEspecifico
                : criacao.toLocalDate().plusDays(diasDaCategoria);
        boolean atrasada = !ENCERRADAS.contains(status) && hoje.isAfter(data);
        long dias = atrasada ? ChronoUnit.DAYS.between(data, hoje) : 0;
        return new Prazo(data, prazoEspecifico != null, atrasada, dias);
    }

    public static Prazo calcular(Denuncia d, Encaminhamento atual, LocalDate hoje) {
        return calcular(d.getDataCriacao(), d.getCategoria().getPrazoReferenciaDias(),
                atual == null ? null : atual.getPrazoEspecifico(), d.getStatus(), hoje);
    }
}