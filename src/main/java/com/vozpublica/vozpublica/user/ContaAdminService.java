package com.vozpublica.vozpublica.user;

import com.vozpublica.vozpublica.auditoria.AcaoAuditoria;
import com.vozpublica.vozpublica.auditoria.AuditoriaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContaAdminService {

    private static final int MAX_JUSTIFICATIVA = 1000;
    private static final String ULTIMO =
            "Esta conta é o último administrador ativo e não pode ser rebaixada nem suspensa.";

    private record Contexto(User responsavel, User alvo, List<User> administradoresAtivos) {}

    private final UserRepository userRepository;
    private final AuditoriaService auditoria;

    public ContaAdminService(UserRepository userRepository, AuditoriaService auditoria) {
        this.userRepository = userRepository;
        this.auditoria = auditoria;
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public void alterarPapel(Long usuarioId, Role novoPapel, String justificativa, String emailResponsavel) {
        if (novoPapel == null) {
            throw new ContaInvalidaException("Escolha o novo perfil.");
        }
        String motivo = justificativaObrigatoria(justificativa);
        Contexto c = preparar(usuarioId, emailResponsavel);
        User alvo = c.alvo();

        if (alvo.getRole() == novoPapel) {
            throw new ContaInvalidaException("A conta já tem o perfil " + novoPapel + ".");
        }
        if (alvo.getRole() == Role.ADMINISTRADOR && alvo.isEnabled()
                && novoPapel != Role.ADMINISTRADOR && c.administradoresAtivos().size() <= 1) {
            throw new ContaInvalidaException(ULTIMO);
        }

        Role anterior = alvo.getRole();
        alvo.alterarPapel(novoPapel);
        auditoria.registrar(emailResponsavel, AcaoAuditoria.PAPEL_ALTERADO, "USUARIO", alvo.getId(),
                motivo, "Usuário #" + alvo.getId() + ": " + anterior + " → " + novoPapel);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public void suspender(Long usuarioId, String justificativa, String emailResponsavel) {
        String motivo = justificativaObrigatoria(justificativa);
        Contexto c = preparar(usuarioId, emailResponsavel);
        User alvo = c.alvo();

        if (!alvo.isEnabled()) {
            throw new ContaInvalidaException("A conta já está suspensa.");
        }
        if (alvo.getRole() == Role.ADMINISTRADOR && c.administradoresAtivos().size() <= 1) {
            throw new ContaInvalidaException(ULTIMO);
        }

        alvo.definirAtivo(false);
        auditoria.registrar(emailResponsavel, AcaoAuditoria.USUARIO_SUSPENSO, "USUARIO", alvo.getId(),
                motivo, "Usuário #" + alvo.getId() + " (perfil " + alvo.getRole() + ")");
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public void reativar(Long usuarioId, String justificativa, String emailResponsavel) {
        String motivo = justificativaObrigatoria(justificativa);
        Contexto c = preparar(usuarioId, emailResponsavel);
        User alvo = c.alvo();

        if (alvo.isEnabled()) {
            throw new ContaInvalidaException("A conta não está suspensa.");
        }

        alvo.definirAtivo(true);
        auditoria.registrar(emailResponsavel, AcaoAuditoria.USUARIO_REATIVADO, "USUARIO", alvo.getId(),
                motivo, "Usuário #" + alvo.getId() + " (perfil " + alvo.getRole() + ")");
    }

    private Contexto preparar(Long usuarioId, String emailResponsavel) {
        User responsavel = userRepository.findByEmail(emailResponsavel.trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));

        // Sempre na mesma ordem (administradores ativos, depois o alvo): requisições
        // concorrentes entram em fila em vez de gerar deadlock, e cada uma enxerga o
        // resultado da anterior. É isso que protege o "último administrador".
        List<User> ativos = userRepository.bloquearAtivosComPapel(Role.ADMINISTRADOR);
        User alvo = userRepository.buscarParaAtualizar(usuarioId)
                .orElseThrow(() -> new ContaInvalidaException("Usuário não encontrado."));

        if (alvo.getId().equals(responsavel.getId())) {
            throw new ContaInvalidaException("Você não pode alterar a sua própria conta.");
        }
        return new Contexto(responsavel, alvo, ativos);
    }

    private static String justificativaObrigatoria(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ContaInvalidaException("Informe a justificativa.");
        }
        String limpo = texto.trim();
        if (limpo.length() > MAX_JUSTIFICATIVA) {
            throw new ContaInvalidaException(
                    "A justificativa deve ter no máximo " + MAX_JUSTIFICATIVA + " caracteres.");
        }
        return limpo;
    }
}