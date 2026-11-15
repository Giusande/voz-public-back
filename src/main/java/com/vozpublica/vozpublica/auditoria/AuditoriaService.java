package com.vozpublica.vozpublica.auditoria;

import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AuditoriaService {

    public static final int TAMANHO_PAGINA = 20;
    private static final int MAX_TEXTO = 2000;

    private final AuditoriaRepository auditoriaRepository;
    private final UserRepository userRepository;

    public AuditoriaService(AuditoriaRepository auditoriaRepository, UserRepository userRepository) {
        this.auditoriaRepository = auditoriaRepository;
        this.userRepository = userRepository;
    }

    /**
     * Só funciona dentro de uma transação já aberta (MANDATORY): o registro é gravado
     * junto com a ação e desfeito junto com ela.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String emailResponsavel, AcaoAuditoria acao, String entidade,
                          Long entidadeId, String justificativa, String detalhes) {
        User responsavel = userRepository.findByEmail(emailResponsavel.trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Responsável não encontrado."));
        auditoriaRepository.save(new RegistroAuditoria(
                responsavel, acao, entidade, entidadeId, limitar(justificativa), limitar(detalhes)));
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public Page<RegistroAuditoria> listar(FiltroAuditoria filtro, int pagina) {
        Specification<RegistroAuditoria> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (filtro.getAcao() != null) {
                ps.add(cb.equal(root.get("acao"), filtro.getAcao()));
            }
            if (filtro.getResponsavel() != null && !filtro.getResponsavel().isBlank()) {
                ps.add(cb.equal(cb.lower(root.get("responsavel").<String>get("email")),
                        filtro.getResponsavel().trim().toLowerCase(Locale.ROOT)));
            }
            if (filtro.getDe() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("dataAcao"), filtro.getDe().atStartOfDay()));
            }
            if (filtro.getAte() != null) {
                ps.add(cb.lessThan(root.get("dataAcao"), filtro.getAte().plusDays(1).atStartOfDay()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return auditoriaRepository.findAll(spec, PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA,
                Sort.by(Sort.Direction.DESC, "dataAcao").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    private static String limitar(String texto) {
        if (texto == null) return null;
        return texto.length() > MAX_TEXTO ? texto.substring(0, MAX_TEXTO) : texto;
    }
}