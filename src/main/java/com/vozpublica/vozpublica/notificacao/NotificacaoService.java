package com.vozpublica.vozpublica.notificacao;

import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificacaoService {

    public static final int TAMANHO_PAGINA = 20;
    private static final int MAX_MENSAGEM = 600;
    private static final int MAX_TRECHO = 80;

    private final NotificacaoRepository notificacaoRepository;
    private final UserRepository userRepository;

    public NotificacaoService(NotificacaoRepository notificacaoRepository, UserRepository userRepository) {
        this.notificacaoRepository = notificacaoRepository;
        this.userRepository = userRepository;
    }

    /**
     * Só funciona dentro de uma transação já aberta (MANDATORY): a notificação nasce junto com a
     * ação e é desfeita junto com ela. Não notifica quem executou a própria ação.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notificar(Long destinatarioId, Long executorId, TipoNotificacao tipo,
                          String mensagem, Long denunciaId) {
        if (destinatarioId == null || destinatarioId.equals(executorId)) {
            return;
        }
        String texto = mensagem.length() > MAX_MENSAGEM ? mensagem.substring(0, MAX_MENSAGEM - 1) + "…" : mensagem;
        notificacaoRepository.save(new Notificacao(destinatarioId, tipo, texto, denunciaId));
    }

    /** Título encurtado para caber nas mensagens. */
    public static String trecho(String titulo) {
        if (titulo == null) return "";
        return titulo.length() > MAX_TRECHO ? titulo.substring(0, MAX_TRECHO) + "…" : titulo;
    }

    @Transactional(readOnly = true)
    public Page<Notificacao> listar(String email, int pagina) {
        return notificacaoRepository.findByUsuarioIdOrderByIdDesc(
                idDe(email), PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA));
    }

    @Transactional(readOnly = true)
    public long naoLidas(String email) {
        return notificacaoRepository.contarNaoLidas(email.trim().toLowerCase());
    }

    @Transactional
    public void marcarLida(String email, Long id) {
        notificacaoRepository.marcarLida(id, idDe(email));
    }

    @Transactional
    public void marcarTodas(String email) {
        notificacaoRepository.marcarTodas(idDe(email));
    }

    private Long idDe(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }
}