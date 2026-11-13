package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.denuncia.ArmazenamentoFotoService.FotoProcessada;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class GestaoDenunciaService {

    private static final int MAX_OBSERVACAO = 2000;
    private static final Set<StatusDenuncia> EXIGEM_OBSERVACAO =
            EnumSet.of(StatusDenuncia.RESOLVIDA, StatusDenuncia.INDEFERIDA, StatusDenuncia.REABERTA);

    private final DenunciaGestaoRepository gestaoRepository;
    private final HistoricoStatusRepository historicoRepository;
    private final HistoricoPrioridadeRepository prioridadeRepository;
    private final EvidenciaRepository evidenciaRepository;
    private final UserRepository userRepository;
    private final ArmazenamentoFotoService armazenamento;

    public GestaoDenunciaService(DenunciaGestaoRepository gestaoRepository,
                                 HistoricoStatusRepository historicoRepository,
                                 HistoricoPrioridadeRepository prioridadeRepository,
                                 EvidenciaRepository evidenciaRepository,
                                 UserRepository userRepository,
                                 ArmazenamentoFotoService armazenamento) {
        this.gestaoRepository = gestaoRepository;
        this.historicoRepository = historicoRepository;
        this.prioridadeRepository = prioridadeRepository;
        this.evidenciaRepository = evidenciaRepository;
        this.userRepository = userRepository;
        this.armazenamento = armazenamento;
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional
    public void alterarStatus(Long denunciaId, StatusDenuncia novoStatus, String observacao,
                              List<MultipartFile> evidencias, String emailResponsavel) {
        if (novoStatus == null) {
            throw new GestaoInvalidaException("Escolha a nova situação.");
        }
        String obs = normalizar(observacao);
        if (EXIGEM_OBSERVACAO.contains(novoStatus) && obs == null) {
            throw new GestaoInvalidaException("Para " + novoStatus + ", a observação é obrigatória.");
        }

        // valida as imagens antes de tocar no banco ou no disco
        List<FotoProcessada> provas = armazenamento.validar(evidencias);
        if (novoStatus == StatusDenuncia.RESOLVIDA && provas.isEmpty()) {
            throw new GestaoInvalidaException(
                    "Para marcar como RESOLVIDA, anexe pelo menos uma foto de evidência.");
        }

        User responsavel = responsavel(emailResponsavel);

        // bloqueia a linha: a validação abaixo vale contra o status mais recente
        Denuncia denuncia = gestaoRepository.buscarParaAtualizar(denunciaId)
                .orElseThrow(DenunciaNaoEncontradaException::new);

        StatusDenuncia atual = denuncia.getStatus();
        if (!atual.podeIrPara(novoStatus)) {
            throw new GestaoInvalidaException(
                    "Transição não permitida: " + atual + " → " + novoStatus + ".");
        }

        denuncia.aplicarStatus(novoStatus);
        HistoricoStatus registro = historicoRepository.save(
                new HistoricoStatus(denuncia, atual, novoStatus, responsavel, obs));

        for (FotoProcessada prova : provas) {
            String nome = armazenamento.salvar(prova);
            apagarSeTransacaoFalhar(nome);
            evidenciaRepository.save(new Evidencia(registro.getId(), nome));
        }
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional
    public void definirPrioridadeOficial(Long denunciaId, Prioridade nova, String observacao,
                                         String emailResponsavel) {
        if (nova == null) {
            throw new GestaoInvalidaException("Escolha a prioridade oficial.");
        }
        String obs = normalizar(observacao);
        User responsavel = responsavel(emailResponsavel);

        Denuncia denuncia = gestaoRepository.buscarParaAtualizar(denunciaId)
                .orElseThrow(DenunciaNaoEncontradaException::new);

        Prioridade anterior = denuncia.getPrioridadeOficial();
        if (anterior == nova) {
            throw new GestaoInvalidaException("A prioridade oficial já é " + nova + ".");
        }

        denuncia.aplicarPrioridadeOficial(nova);
        prioridadeRepository.save(
                new HistoricoPrioridade(denunciaId, anterior, nova, responsavel, obs));
    }

    private User responsavel(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }

    private String normalizar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String limpo = texto.trim();
        if (limpo.length() > MAX_OBSERVACAO) {
            throw new GestaoInvalidaException(
                    "A observação deve ter no máximo " + MAX_OBSERVACAO + " caracteres.");
        }
        return limpo;
    }

    private void apagarSeTransacaoFalhar(String nomeArquivo) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    armazenamento.apagar(nomeArquivo);
                }
            }
        });
    }
}