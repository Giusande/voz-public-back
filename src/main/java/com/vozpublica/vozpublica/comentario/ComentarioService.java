package com.vozpublica.vozpublica.comentario;

import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaNaoEncontradaException;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.notificacao.NotificacaoService;
import com.vozpublica.vozpublica.notificacao.TipoNotificacao;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComentarioService {

    private final ComentarioRepository comentarioRepository;
    private final DenunciaRepository denunciaRepository;
    private final UserRepository userRepository;
    private final NotificacaoService notificacoes;

    public ComentarioService(ComentarioRepository comentarioRepository,
                             DenunciaRepository denunciaRepository,
                             UserRepository userRepository,
                             NotificacaoService notificacoes) {
        this.comentarioRepository = comentarioRepository;
        this.denunciaRepository = denunciaRepository;
        this.userRepository = userRepository;
        this.notificacoes = notificacoes;
    }

    @Transactional
    public Comentario adicionar(Long denunciaId, String emailAutor, String conteudo) {
        Denuncia denuncia = denunciaRepository.findById(denunciaId)
                .filter(d -> !d.isOculta() && !d.isAgrupada())
                .orElseThrow(DenunciaNaoEncontradaException::new);
        User autor = userRepository.findByEmail(emailAutor.trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));

        Comentario salvo = comentarioRepository.save(new Comentario(denunciaId, autor, conteudo.trim()));

        notificacoes.notificar(denuncia.getAutor().getId(), autor.getId(), TipoNotificacao.COMENTARIO,
                "Novo comentário na sua ocorrência “" + NotificacaoService.trecho(denuncia.getTitulo()) + "”.",
                denunciaId);
        return salvo;
    }
}