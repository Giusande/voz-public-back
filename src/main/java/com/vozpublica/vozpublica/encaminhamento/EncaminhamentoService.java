package com.vozpublica.vozpublica.encaminhamento;

import com.vozpublica.vozpublica.denuncia.DenunciaGestaoRepository;
import com.vozpublica.vozpublica.denuncia.DenunciaNaoEncontradaException;
import com.vozpublica.vozpublica.denuncia.GestaoInvalidaException;
import com.vozpublica.vozpublica.denuncia.StatusDenuncia;
import com.vozpublica.vozpublica.unidade.Unidade;
import com.vozpublica.vozpublica.unidade.UnidadeRepository;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

@Service
public class EncaminhamentoService {

    private static final int MAX_OBSERVACAO = 2000;
    private static final int MAX_DIAS_PRAZO = 365;

    private final DenunciaGestaoRepository gestaoRepository;
    private final EncaminhamentoRepository encaminhamentoRepository;
    private final UnidadeRepository unidadeRepository;
    private final UserRepository userRepository;

    public EncaminhamentoService(DenunciaGestaoRepository gestaoRepository,
                                 EncaminhamentoRepository encaminhamentoRepository,
                                 UnidadeRepository unidadeRepository,
                                 UserRepository userRepository) {
        this.gestaoRepository = gestaoRepository;
        this.encaminhamentoRepository = encaminhamentoRepository;
        this.unidadeRepository = unidadeRepository;
        this.userRepository = userRepository;
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional
    public void encaminhar(Long denunciaId, Long unidadeId, Long servidorId,
                           LocalDate prazoEspecifico, String observacao, String emailResponsavel) {
        if (unidadeId == null) {
            throw new GestaoInvalidaException("Escolha a unidade responsável.");
        }
        Unidade unidade = unidadeRepository.findById(unidadeId).filter(Unidade::isAtiva)
                .orElseThrow(() -> new GestaoInvalidaException("Unidade inválida ou desativada."));

        User servidor = null;
        if (servidorId != null) {
            servidor = userRepository.findById(servidorId)
                    .filter(u -> u.isEnabled()
                            && (u.getRole() == Role.SERVIDOR || u.getRole() == Role.ADMINISTRADOR))
                    .orElseThrow(() -> new GestaoInvalidaException("Servidor responsável inválido."));
        }

        LocalDate hoje = LocalDate.now();
        if (prazoEspecifico != null
                && (prazoEspecifico.isBefore(hoje) || prazoEspecifico.isAfter(hoje.plusDays(MAX_DIAS_PRAZO)))) {
            throw new GestaoInvalidaException(
                    "O prazo específico deve ficar entre hoje e " + MAX_DIAS_PRAZO + " dias à frente.");
        }

        String obs = normalizar(observacao);
        User responsavel = userRepository.findByEmail(emailResponsavel.trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));

        // bloqueia a linha: a validação abaixo vale contra o estado mais recente
        var denuncia = gestaoRepository.buscarParaAtualizar(denunciaId)
                .orElseThrow(DenunciaNaoEncontradaException::new);

        StatusDenuncia status = denuncia.getStatus();
        if (status == StatusDenuncia.RESOLVIDA || status == StatusDenuncia.INDEFERIDA) {
            throw new GestaoInvalidaException(
                    "A ocorrência está " + status + ". Reabra antes de encaminhar.");
        }

        Optional<Encaminhamento> atual = encaminhamentoRepository.findFirstByDenunciaIdOrderByIdDesc(denunciaId);
        if (atual.isPresent() && igual(atual.get(), unidade, servidor, prazoEspecifico)) {
            throw new GestaoInvalidaException("Esse encaminhamento é igual ao atual.");
        }

        encaminhamentoRepository.save(
                new Encaminhamento(denunciaId, unidade, servidor, prazoEspecifico, obs, responsavel));
    }

    private static boolean igual(Encaminhamento a, Unidade unidade, User servidor, LocalDate prazo) {
        Long servidorAtual = a.getServidor() == null ? null : a.getServidor().getId();
        Long servidorNovo = servidor == null ? null : servidor.getId();
        return Objects.equals(a.getUnidade().getId(), unidade.getId())
                && Objects.equals(servidorAtual, servidorNovo)
                && Objects.equals(a.getPrazoEspecifico(), prazo);
    }

    private static String normalizar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String limpo = texto.trim();
        if (limpo.length() > MAX_OBSERVACAO) {
            throw new GestaoInvalidaException(
                    "A observação deve ter no máximo " + MAX_OBSERVACAO + " caracteres.");
        }
        return limpo;
    }
}