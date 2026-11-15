package com.vozpublica.vozpublica.moderacao;

import com.vozpublica.vozpublica.comentario.Comentario;
import com.vozpublica.vozpublica.comentario.ComentarioRepository;
import com.vozpublica.vozpublica.denuncia.DenunciaNaoEncontradaException;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SinalizacaoService {

    private static final int MAX_DETALHE = 500;
    private static final int MAX_POR_DIA = 20;

    private final SinalizacaoRepository sinalizacaoRepository;
    private final DenunciaRepository denunciaRepository;
    private final ComentarioRepository comentarioRepository;
    private final UserRepository userRepository;

    public SinalizacaoService(SinalizacaoRepository sinalizacaoRepository,
                              DenunciaRepository denunciaRepository,
                              ComentarioRepository comentarioRepository,
                              UserRepository userRepository) {
        this.sinalizacaoRepository = sinalizacaoRepository;
        this.denunciaRepository = denunciaRepository;
        this.comentarioRepository = comentarioRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void sinalizarDenuncia(Long denunciaId, MotivoSinalizacao motivo, String detalhe, String email) {
        if (!denunciaRepository.existsByIdAndOcultaFalse(denunciaId)) {
            throw new DenunciaNaoEncontradaException();
        }
        registrar(TipoConteudo.DENUNCIA, denunciaId, denunciaId, motivo, detalhe, email);
    }

    @Transactional
    public void sinalizarComentario(Long denunciaId, Long comentarioId, MotivoSinalizacao motivo,
                                    String detalhe, String email) {
        if (!denunciaRepository.existsByIdAndOcultaFalse(denunciaId)) {
            throw new DenunciaNaoEncontradaException();
        }
        Comentario comentario = comentarioRepository.findById(comentarioId)
                .filter(c -> !c.isOculto() && c.getDenunciaId().equals(denunciaId))
                .orElseThrow(() -> new ModeracaoException("Comentário não encontrado."));
        registrar(TipoConteudo.COMENTARIO, comentario.getId(), denunciaId, motivo, detalhe, email);
    }

    private void registrar(TipoConteudo tipo, Long alvoId, Long denunciaId, MotivoSinalizacao motivo,
                           String detalhe, String email) {
        if (motivo == null) {
            throw new ModeracaoException("Escolha o motivo da sinalização.");
        }
        String texto = detalhe == null ? "" : detalhe.trim();
        if (texto.length() > MAX_DETALHE) {
            throw new ModeracaoException("Os detalhes devem ter no máximo " + MAX_DETALHE + " caracteres.");
        }

        Long usuarioId = userRepository.findByEmail(email.trim().toLowerCase())
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));

        if (sinalizacaoRepository.countByUsuarioIdAndDataCriacaoAfter(
                usuarioId, LocalDateTime.now().minusDays(1)) >= MAX_POR_DIA) {
            throw new ModeracaoException("Limite diário de sinalizações atingido. Tente novamente amanhã.");
        }

        if (sinalizacaoRepository.inserir(tipo.name(), alvoId, denunciaId, usuarioId,
                motivo.name(), texto) == 0) {
            throw new ModeracaoException("Você já sinalizou este conteúdo.");
        }
    }
}