package com.vozpublica.vozpublica.confirmacao;

import com.vozpublica.vozpublica.denuncia.DenunciaNaoEncontradaException;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmacaoService {

    public record Resumo(long tambemExiste, long continua, long resolvido, TipoConfirmacao meuVoto) {
        public long de(TipoConfirmacao tipo) {
            return switch (tipo) {
                case TAMBEM_EXISTE -> tambemExiste;
                case CONTINUA_PRESENTE -> continua;
                case APARENTEMENTE_RESOLVIDO -> resolvido;
            };
        }
    }

    private final ConfirmacaoRepository confirmacaoRepository;
    private final DenunciaRepository denunciaRepository;
    private final UserRepository userRepository;

    public ConfirmacaoService(ConfirmacaoRepository confirmacaoRepository,
                              DenunciaRepository denunciaRepository, UserRepository userRepository) {
        this.confirmacaoRepository = confirmacaoRepository;
        this.denunciaRepository = denunciaRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void registrar(Long denunciaId, TipoConfirmacao tipo, String email) {
        garantir(denunciaId);
        if (tipo == null) {
            throw new ConfirmacaoException("Escolha uma opção.");
        }
        confirmacaoRepository.registrar(denunciaId, usuarioId(email), tipo.name());
    }

    @Transactional
    public void remover(Long denunciaId, String email) {
        garantir(denunciaId);
        confirmacaoRepository.remover(denunciaId, usuarioId(email));
    }

    /** emailOuNull = visitante ou equipe sem interesse no voto. */
    @Transactional(readOnly = true)
    public Resumo resumo(Long denunciaId, String emailOuNull) {
        TipoConfirmacao meu = null;
        if (emailOuNull != null) {
            meu = userRepository.findByEmail(emailOuNull.trim().toLowerCase())
                    .flatMap(u -> confirmacaoRepository.findByDenunciaIdAndUsuarioId(denunciaId, u.getId()))
                    .map(Confirmacao::getTipo).orElse(null);
        }
        return new Resumo(
                confirmacaoRepository.countByDenunciaIdAndTipo(denunciaId, TipoConfirmacao.TAMBEM_EXISTE),
                confirmacaoRepository.countByDenunciaIdAndTipo(denunciaId, TipoConfirmacao.CONTINUA_PRESENTE),
                confirmacaoRepository.countByDenunciaIdAndTipo(denunciaId, TipoConfirmacao.APARENTEMENTE_RESOLVIDO),
                meu);
    }

    /** Como curtir e incentivar: não vale em ocorrência oculta nem duplicada. */
    private void garantir(Long denunciaId) {
        if (!denunciaRepository.existsByIdAndOcultaFalseAndDuplicadaDeIdIsNull(denunciaId)) {
            throw new DenunciaNaoEncontradaException();
        }
    }

    private Long usuarioId(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }
}