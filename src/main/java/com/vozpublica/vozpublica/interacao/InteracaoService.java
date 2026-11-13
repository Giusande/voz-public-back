package com.vozpublica.vozpublica.interacao;

import com.vozpublica.vozpublica.denuncia.DenunciaNaoEncontradaException;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InteracaoService {

    public record Resumo(long curtidas, long incentivos, boolean curtiu, boolean incentivou) {}

    private final CurtidaRepository curtidaRepository;
    private final IncentivoRepository incentivoRepository;
    private final DenunciaRepository denunciaRepository;
    private final UserRepository userRepository;

    public InteracaoService(CurtidaRepository curtidaRepository,
                            IncentivoRepository incentivoRepository,
                            DenunciaRepository denunciaRepository,
                            UserRepository userRepository) {
        this.curtidaRepository = curtidaRepository;
        this.incentivoRepository = incentivoRepository;
        this.denunciaRepository = denunciaRepository;
        this.userRepository = userRepository;
    }

    /** Curte ou descurte. Retorna true se a ocorrência ficou curtida. */
    @Transactional
    public boolean alternarCurtida(Long denunciaId, String email) {
        garantirDenuncia(denunciaId);
        Long usuarioId = usuarioId(email);

        if (curtidaRepository.remover(denunciaId, usuarioId) > 0) {
            return false;
        }
        curtidaRepository.inserir(denunciaId, usuarioId);
        return true;
    }

    /** Incentiva uma única vez. Retorna true se este foi o incentivo gravado. */
    @Transactional
    public boolean incentivar(Long denunciaId, String email) {
        garantirDenuncia(denunciaId);
        Long usuarioId = usuarioId(email);
        return incentivoRepository.inserir(denunciaId, usuarioId) > 0;
    }

    /** emailOuNull = visitante (sem login). */
    @Transactional(readOnly = true)
    public Resumo resumo(Long denunciaId, String emailOuNull) {
        long curtidas = curtidaRepository.countByDenunciaId(denunciaId);
        long incentivos = incentivoRepository.countByDenunciaId(denunciaId);

        if (emailOuNull == null) {
            return new Resumo(curtidas, incentivos, false, false);
        }
        Long usuarioId = usuarioId(emailOuNull);
        return new Resumo(curtidas, incentivos,
                curtidaRepository.existsByDenunciaIdAndUsuarioId(denunciaId, usuarioId),
                incentivoRepository.existsByDenunciaIdAndUsuarioId(denunciaId, usuarioId));
    }

    private void garantirDenuncia(Long denunciaId) {
        if (!denunciaRepository.existsById(denunciaId)) {
            throw new DenunciaNaoEncontradaException();
        }
    }

    private Long usuarioId(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }
}