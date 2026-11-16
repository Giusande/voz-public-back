package com.vozpublica.vozpublica.solicitacao;

import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaGestaoRepository;
import com.vozpublica.vozpublica.denuncia.DenunciaNaoEncontradaException;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.denuncia.GestaoInvalidaException;
import com.vozpublica.vozpublica.notificacao.NotificacaoService;
import com.vozpublica.vozpublica.notificacao.TipoNotificacao;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SolicitacaoInfoService {

    private static final int MAX_MENSAGEM = 1000;
    private static final int MAX_RESPOSTA = 2000;
    private static final String JA_ABERTA = "Já existe uma solicitação aguardando resposta nesta ocorrência.";

    private final SolicitacaoInfoRepository solicitacaoRepository;
    private final DenunciaGestaoRepository gestaoRepository;
    private final DenunciaRepository denunciaRepository;
    private final UserRepository userRepository;
    private final NotificacaoService notificacoes;

    public SolicitacaoInfoService(SolicitacaoInfoRepository solicitacaoRepository,
                                  DenunciaGestaoRepository gestaoRepository,
                                  DenunciaRepository denunciaRepository,
                                  UserRepository userRepository,
                                  NotificacaoService notificacoes) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.gestaoRepository = gestaoRepository;
        this.denunciaRepository = denunciaRepository;
        this.userRepository = userRepository;
        this.notificacoes = notificacoes;
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional
    public void solicitar(Long denunciaId, String mensagem, String email) {
        String texto = mensagem == null ? "" : mensagem.trim();
        if (texto.isEmpty()) {
            throw new GestaoInvalidaException("Escreva o que precisa ser esclarecido.");
        }
        if (texto.length() > MAX_MENSAGEM) {
            throw new GestaoInvalidaException("A mensagem deve ter no máximo " + MAX_MENSAGEM + " caracteres.");
        }
        User solicitante = usuario(email);

        // bloqueia a ocorrência: pedidos simultâneos entram em fila
        Denuncia d = gestaoRepository.buscarParaAtualizar(denunciaId)
                .orElseThrow(DenunciaNaoEncontradaException::new);
        if (d.isOculta()) {
            throw new GestaoInvalidaException("Não é possível pedir informações de uma ocorrência oculta.");
        }
        if (d.isAgrupada()) {
            throw new GestaoInvalidaException("Esta ocorrência está agrupada à #" + d.getDuplicadaDeId()
                    + ". Gerencie a principal ou desagrupe antes.");
        }
        if (solicitacaoRepository.existsByDenunciaIdAndRespostaIsNull(denunciaId)) {
            throw new GestaoInvalidaException(JA_ABERTA);
        }

        try {
            solicitacaoRepository.saveAndFlush(new SolicitacaoInfo(denunciaId, solicitante, texto));
        } catch (DataIntegrityViolationException e) {
            throw new GestaoInvalidaException(JA_ABERTA); // o índice único do banco é a rede de segurança
        }

        notificacoes.notificar(d.getAutor().getId(), solicitante.getId(), TipoNotificacao.INFORMACAO,
                "A equipe pediu informações adicionais sobre a sua ocorrência “"
                        + NotificacaoService.trecho(d.getTitulo()) + "”. Responda em Minhas denúncias.",
                denunciaId);
    }

    /** Só o autor da ocorrência responde. */
    @Transactional
    public void responder(Long denunciaId, String resposta, String email) {
        String texto = resposta == null ? "" : resposta.trim();
        if (texto.isEmpty()) {
            throw new GestaoInvalidaException("Escreva a sua resposta.");
        }
        if (texto.length() > MAX_RESPOSTA) {
            throw new GestaoInvalidaException("A resposta deve ter no máximo " + MAX_RESPOSTA + " caracteres.");
        }
        User autor = usuario(email);
        Denuncia d = denunciaRepository.findById(denunciaId)
                .filter(x -> x.getAutor().getId().equals(autor.getId()))
                .orElseThrow(() -> new GestaoInvalidaException("Ocorrência não encontrada."));
        String titulo = NotificacaoService.trecho(d.getTitulo());

        if (solicitacaoRepository.responder(denunciaId, texto, LocalDateTime.now()) == 0) {
            throw new GestaoInvalidaException("Não há solicitação aguardando resposta.");
        }
        solicitacaoRepository.findFirstByDenunciaIdOrderByIdDesc(denunciaId).ifPresent(s ->
                notificacoes.notificar(s.getSolicitante().getId(), autor.getId(),
                        TipoNotificacao.RESPOSTA_INFORMACAO,
                        "O autor respondeu ao pedido de informações sobre “" + titulo + "”.", denunciaId));
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public List<SolicitacaoInfo> doPainel(Long denunciaId) {
        return solicitacaoRepository.findByDenunciaIdOrderByIdDesc(denunciaId);
    }

    private User usuario(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }
}