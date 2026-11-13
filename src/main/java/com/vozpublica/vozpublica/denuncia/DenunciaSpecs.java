package com.vozpublica.vozpublica.denuncia;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import com.vozpublica.vozpublica.interacao.Incentivo;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DenunciaSpecs {

    private DenunciaSpecs() {}

    public static Specification<Denuncia> de(FiltroDenuncia f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();

            if (f.getBairro() != null && !f.getBairro().isBlank()) {
                String termo = f.getBairro().trim();
                if (termo.length() > 100) termo = termo.substring(0, 100);
                ps.add(cb.like(cb.lower(root.<String>get("bairro")),
                        "%" + escapar(termo.toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            if (f.getRegiao() != null && !f.getRegiao().isBlank()) {
                ps.add(cb.equal(root.get("regiao"), f.getRegiao()));
            }
            if (f.getCategoriaId() != null) {
                ps.add(cb.equal(root.get("categoria").get("id"), f.getCategoriaId()));
            }
            if (f.getPrioridade() != null) {
                ps.add(prioridadeEfetiva(root, cb, f.getPrioridade()));
            }
            if (f.getStatus() != null) {
                ps.add(cb.equal(root.get("status"), f.getStatus()));
            }
            if (f.getDe() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("dataCriacao"),
                        f.getDe().atStartOfDay()));
            }
            if (f.getAte() != null) {
                ps.add(cb.lessThan(root.<LocalDateTime>get("dataCriacao"),
                        f.getAte().plusDays(1).atStartOfDay()));
            }

            // A ordenação só vale na consulta de dados, não na de contagem da paginação
            Class<?> tipo = query.getResultType();
            boolean contagem = Long.class.equals(tipo) || long.class.equals(tipo);
            if (!contagem) {
                switch (f.getOrdem()) {
                    case INCENTIVADAS -> {
                        Subquery<Long> total = query.subquery(Long.class);
                        Root<Incentivo> inc = total.from(Incentivo.class);
                        total.select(cb.count(inc))
                             .where(cb.equal(inc.get("denunciaId"), root.get("id")));
                        query.orderBy(cb.desc(total),
                                cb.desc(root.get("dataCriacao")), cb.desc(root.get("id")));
                    }
                    case URGENTES -> {
                        var peso = cb.<Integer>selectCase()
                                .when(prioridadeEfetiva(root, cb, Prioridade.URGENTE), 4)
                                .when(prioridadeEfetiva(root, cb, Prioridade.ALTA), 3)
                                .when(prioridadeEfetiva(root, cb, Prioridade.MEDIA), 2)
                                .otherwise(1);
                        query.orderBy(cb.desc(peso),
                                cb.desc(root.get("dataCriacao")), cb.desc(root.get("id")));
                    }
                    default -> query.orderBy(
                            cb.desc(root.get("dataCriacao")), cb.desc(root.get("id")));
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /** Prioridade oficial, se existir; senão a sugerida pelo cidadão. */
    private static Predicate prioridadeEfetiva(Root<Denuncia> root, CriteriaBuilder cb, Prioridade p) {
        return cb.or(
                cb.equal(root.get("prioridadeOficial"), p),
                cb.and(cb.isNull(root.get("prioridadeOficial")),
                       cb.equal(root.get("prioridadeSugerida"), p)));
    }

    /** Impede que % e _ digitados pelo usuário funcionem como curingas do LIKE. */
    private static String escapar(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}