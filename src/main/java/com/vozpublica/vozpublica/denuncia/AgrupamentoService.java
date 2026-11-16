package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.auditoria.AcaoAuditoria;
import com.vozpublica.vozpublica.auditoria.AuditoriaService;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgrupamentoService {

    private static final int MAX_JUSTIFICATIVA = 1000;

    private final DenunciaGestaoRepository gestaoRepository;
    private final DenunciaRepository denunciaRepository;
    private final AgrupamentoRepository agrupamentoRepository;
    private final UserRepository userRepository;
    private final AuditoriaService auditoria;

    public AgrupamentoService(DenunciaGestaoRepository gestaoRepository,
                              DenunciaRepository denunciaRepository,
                              AgrupamentoRepository agrupamentoRepository,
                              UserRepository userRepository,
                              AuditoriaService auditoria) {
        this.gestaoRepository = gestaoRepository;
        this.denunciaRepository = denunciaRepository;
        this.agrupamentoRepository = agrupamentoRepository;
        this.userRepository = userRepository;
        this.auditoria = auditoria;
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional
    public void agrupar(Long filhaId, Long principalId, String justificativa, String email) {
        if (principalId == null) {
            throw new GestaoInvalidaException("Informe o número da ocorrência principal.");
        }
        if (filhaId.equals(principalId)) {
            throw new GestaoInvalidaException("Uma ocorrência não pode ser agrupada a ela mesma.");
        }
        String motivo = justificativaObrigatoria(justificativa);
        User responsavel = responsavel(email);

        if (!denunciaRepository.existsById(principalId)) {
            throw new GestaoInvalidaException("Ocorrência principal não encontrada.");
        }

        // Bloqueia as duas linhas sempre em ordem de id: agrupamentos simultâneos em
        // sentidos opostos entram em fila em vez de gerar deadlock.
        Long primeiro = Math.min(filhaId, principalId);
        Long segundo = Math.max(filhaId, principalId);
        Denuncia a = gestaoRepository.buscarParaAtualizar(primeiro)
                .orElseThrow(DenunciaNaoEncontradaException::new);
        Denuncia b = gestaoRepository.buscarParaAtualizar(segundo)
                .orElseThrow(DenunciaNaoEncontradaException::new);
        Denuncia filha = a.getId().equals(filhaId) ? a : b;
        Denuncia principal = filha == a ? b : a;

        if (filha.isAgrupada()) {
            throw new GestaoInvalidaException("Esta ocorrência já está agrupada à #"
                    + filha.getDuplicadaDeId() + ". Desagrupe antes de agrupar de novo.");
        }
        if (principal.isAgrupada()) {
            throw new GestaoInvalidaException("A ocorrência #" + principalId
                    + " é duplicada da #" + principal.getDuplicadaDeId() + ". Use aquela como principal.");
        }
        if (filha.isOculta() || principal.isOculta()) {
            throw new GestaoInvalidaException("Não é possível agrupar ocorrências ocultas.");
        }
        if (denunciaRepository.existsByDuplicadaDeId(filhaId)) {
            throw new GestaoInvalidaException(
                    "Esta ocorrência já reúne duplicadas. Desagrupe-as antes de agrupá-la a outra.");
        }

        filha.definirDuplicadaDe(principalId);
        agrupamentoRepository.save(
                new Agrupamento(filhaId, principalId, TipoAgrupamento.AGRUPADA, responsavel, motivo));
        auditoria.registrar(email, AcaoAuditoria.DUPLICIDADE_AGRUPADA, "DENUNCIA", filhaId, motivo,
                "Ocorrência #" + filhaId + " agrupada à #" + principalId);
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional
    public void desagrupar(Long filhaId, String justificativa, String email) {
        String motivo = justificativaObrigatoria(justificativa);
        User responsavel = responsavel(email);

        Denuncia filha = gestaoRepository.buscarParaAtualizar(filhaId)
                .orElseThrow(DenunciaNaoEncontradaException::new);
        if (!filha.isAgrupada()) {
            throw new GestaoInvalidaException("Esta ocorrência não está agrupada.");
        }

        Long principalId = filha.getDuplicadaDeId();
        filha.definirDuplicadaDe(null);
        agrupamentoRepository.save(
                new Agrupamento(filhaId, principalId, TipoAgrupamento.DESAGRUPADA, responsavel, motivo));
        auditoria.registrar(email, AcaoAuditoria.DUPLICIDADE_DESAGRUPADA, "DENUNCIA", filhaId, motivo,
                "Ocorrência #" + filhaId + " desagrupada da #" + principalId);
    }

    private User responsavel(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }

    private static String justificativaObrigatoria(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new GestaoInvalidaException("Informe a justificativa.");
        }
        String limpo = texto.trim();
        if (limpo.length() > MAX_JUSTIFICATIVA) {
            throw new GestaoInvalidaException(
                    "A justificativa deve ter no máximo " + MAX_JUSTIFICATIVA + " caracteres.");
        }
        return limpo;
    }
}