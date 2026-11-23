package com.vozpublica.vozpublica.encaminhamento;

import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.StatusDenuncia;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

public record Prazo(LocalDate data, boolean doEncaminhamento, boolean aPartirDaReabertura,
                    boolean atrasada, long diasAtraso) {

    private static final Set<StatusDenuncia> ENCERRADAS =
            EnumSet.of(StatusDenuncia.RESOLVIDA, StatusDenuncia.INDEFERIDA);

    public static Prazo calcular(LocalDateTime base, boolean baseEhReabertura, int diasDaCategoria,
                                 LocalDate prazoEspecifico, StatusDenuncia status, LocalDate hoje) {
        LocalDate data = prazoEspecifico != null
                ? prazoEspecifico
                : base.toLocalDate().plusDays(diasDaCategoria);
        boolean atrasada = !ENCERRADAS.contains(status) && hoje.isAfter(data);
        long dias = atrasada ? ChronoUnit.DAYS.between(data, hoje) : 0;
        return new Prazo(data, prazoEspecifico != null,
                baseEhReabertura && prazoEspecifico == null, atrasada, dias);
    }

    /** Sem reabertura: a base é a criação. */
    public static Prazo calcular(LocalDateTime criacao, int diasDaCategoria, LocalDate prazoEspecifico,
                                 StatusDenuncia status, LocalDate hoje) {
        return calcular(criacao, false, diasDaCategoria, prazoEspecifico, status, hoje);
    }

    public static Prazo calcular(Denuncia d, Encaminhamento atual, LocalDateTime ultimaReabertura,
                                 LocalDate hoje) {
        LocalDate especifico = null;
        if (atual != null && atual.getPrazoEspecifico() != null
                && (ultimaReabertura == null || atual.getDataEncaminhamento().isAfter(ultimaReabertura))) {
            especifico = atual.getPrazoEspecifico();
        }
        LocalDateTime base = ultimaReabertura != null ? ultimaReabertura : d.getDataCriacao();
        return calcular(base, ultimaReabertura != null, d.getCategoria().getPrazoReferenciaDias(),
                especifico, d.getStatus(), hoje);
    }

    /** Sem considerar reabertura (usado apenas por testes antigos). */
    public static Prazo calcular(Denuncia d, Encaminhamento atual, LocalDate hoje) {
        return calcular(d, atual, null, hoje);
    }
}