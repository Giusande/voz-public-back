package com.vozpublica.vozpublica.moderacao;

import com.vozpublica.vozpublica.auditoria.AcaoAuditoria;
import com.vozpublica.vozpublica.auditoria.AuditoriaService;
import com.vozpublica.vozpublica.comentario.Comentario;
import com.vozpublica.vozpublica.comentario.ComentarioRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaGestaoRepository;
import com.vozpublica.vozpublica.denuncia.DenunciaNaoEncontradaException;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import com.vozpublica.vozpublica.notificacao.NotificacaoService;
import com.vozpublica.vozpublica.notificacao.TipoNotificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ModeracaoService {

    public static final int TAMANHO_PAGINA = 20;
    private static final int MAX_JUSTIFICATIVA = 1000;
    private static final int MAX_TRECHO = 300;

    /** Não inclui quem sinalizou, de propósito (LGPD). */
    public record ItemFila(Long id, TipoConteudo tipo, Long alvoId, Long denunciaId,
                           String tituloPublicacao, String trecho,
                           MotivoSinalizacao motivo, String detalhe, LocalDateTime data) {}

    private final DenunciaGestaoRepository gestaoRepository;
    private final DenunciaRepository denunciaRepository;
    private final ComentarioRepository comentarioRepository;
    private final SinalizacaoRepository sinalizacaoRepository;
    private final UserRepository userRepository;
    private final AuditoriaService auditoria;
    private final NotificacaoService notificacoes;

    public ModeracaoService(DenunciaGestaoRepository gestaoRepository,
                            DenunciaRepository denunciaRepository,
                            ComentarioRepository comentarioRepository,
                            SinalizacaoRepository sinalizacaoRepository,
                            UserRepository userRepository,
                            AuditoriaService auditoria,
                            NotificacaoService notificacoes) {
        this.gestaoRepository = gestaoRepository;
        this.denunciaRepository = denunciaRepository;
        this.comentarioRepository = comentarioRepository;
        this.sinalizacaoRepository = sinalizacaoRepository;
        this.userRepository = userRepository;
        this.auditoria = auditoria;
        this.notificacoes = notificacoes;
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public Page<ItemFila> fila(int pagina) {
        Page<Sinalizacao> page = sinalizacaoRepository.findByStatusOrderByIdAsc(
                StatusSinalizacao.ABERTA, PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA));

        // Uma consulta por tipo para a página inteira, sem N+1
        Set<Long> idsDenuncia = new HashSet<>();
        Set<Long> idsComentario = new HashSet<>();
        for (Sinalizacao s : page) {
            idsDenuncia.add(s.getDenunciaId());
            if (s.getTipo() == TipoConteudo.COMENTARIO) idsComentario.add(s.getAlvoId());
        }
        Map<Long, Denuncia> denuncias = denunciaRepository.findAllById(idsDenuncia).stream()
                .collect(Collectors.toMap(Denuncia::getId, Function.identity()));
        Map<Long, Comentario> comentarios = comentarioRepository.findAllById(idsComentario).stream()
                .collect(Collectors.toMap(Comentario::getId, Function.identity()));

        return page.map(s -> {
            Denuncia d = denuncias.get(s.getDenunciaId());
            String titulo = d != null ? d.getTitulo() : "(indisponível)";
            String texto;
            if (s.getTipo() == TipoConteudo.DENUNCIA) {
                texto = d != null ? d.getDescricao() : "(indisponível)";
            } else {
                Comentario c = comentarios.get(s.getAlvoId());
                texto = c != null ? c.getConteudo() : "(indisponível)";
            }
            return new ItemFila(s.getId(), s.getTipo(), s.getAlvoId(), s.getDenunciaId(),
                    titulo, cortar(texto), s.getMotivo(), s.getDetalhe(), s.getDataCriacao());
        });
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public void ocultar(TipoConteudo tipo, Long alvoId, String justificativa, String email) {
        exigirTipo(tipo);
        String motivo = justificativaObrigatoria(justificativa);
        aplicarOcultacao(tipo, alvoId, motivo, admin(email), email);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public void ocultarPelaSinalizacao(Long sinalizacaoId, String justificativa, String email) {
        String motivo = justificativaObrigatoria(justificativa);
        User admin = admin(email);
        Sinalizacao s = sinalizacaoRepository.findById(sinalizacaoId)
                .orElseThrow(() -> new ModeracaoException("Sinalização não encontrada."));
        if (s.getStatus() != StatusSinalizacao.ABERTA) {
            throw new ModeracaoException("Esta sinalização já foi analisada.");
        }
        aplicarOcultacao(s.getTipo(), s.getAlvoId(), motivo, admin, email);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public void restaurar(TipoConteudo tipo, Long alvoId, String justificativa, String email) {
        exigirTipo(tipo);
        String motivo = justificativaObrigatoria(justificativa);
        User admin = admin(email);

        String detalhes;
        if (tipo == TipoConteudo.DENUNCIA) {
            Denuncia d = gestaoRepository.buscarParaAtualizar(alvoId)
                    .orElseThrow(DenunciaNaoEncontradaException::new);
            if (!d.isOculta()) {
                throw new ModeracaoException("A publicação não está oculta.");
            }
            d.definirOculta(false);
            detalhes = "Publicação #" + alvoId + " restaurada";
            notificacoes.notificar(d.getAutor().getId(), admin.getId(), TipoNotificacao.MODERACAO,
                    "Sua publicação “" + NotificacaoService.trecho(d.getTitulo())
                            + "” foi restaurada pela moderação.", alvoId);
        } else {
            Comentario c = comentarioRepository.buscarParaAtualizar(alvoId)
                    .orElseThrow(() -> new ModeracaoException("Comentário não encontrado."));
            if (!c.isOculto()) {
                throw new ModeracaoException("O comentário não está oculto.");
            }
            c.definirOculto(false);
            detalhes = "Comentário #" + alvoId + " (publicação #" + c.getDenunciaId() + ") restaurado";
            notificarComentario(c, admin, "foi restaurado pela moderação.");
        }
        auditoria.registrar(email, AcaoAuditoria.CONTEUDO_RESTAURADO, tipo.name(), alvoId, motivo, detalhes);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Transactional
    public void descartar(Long sinalizacaoId, String justificativa, String email) {
        String motivo = justificativaObrigatoria(justificativa);
        User admin = admin(email);
        Sinalizacao s = sinalizacaoRepository.buscarParaAtualizar(sinalizacaoId)
                .orElseThrow(() -> new ModeracaoException("Sinalização não encontrada."));
        if (s.getStatus() != StatusSinalizacao.ABERTA) {
            throw new ModeracaoException("Esta sinalização já foi analisada.");
        }
        s.encerrar(StatusSinalizacao.IMPROCEDENTE, admin.getId(), motivo);
        auditoria.registrar(email, AcaoAuditoria.SINALIZACAO_DESCARTADA, "SINALIZACAO", s.getId(), motivo,
                s.getTipo().getRotulo() + " #" + s.getAlvoId() + " mantido(a) no ar");
    }

    private void aplicarOcultacao(TipoConteudo tipo, Long alvoId, String motivo, User admin, String email) {
        String detalhes;
        if (tipo == TipoConteudo.DENUNCIA) {
            Denuncia d = gestaoRepository.buscarParaAtualizar(alvoId)
                    .orElseThrow(DenunciaNaoEncontradaException::new);
            if (d.isOculta()) {
                throw new ModeracaoException("A publicação já está oculta.");
            }
            d.definirOculta(true);
            detalhes = "Publicação #" + alvoId + " ocultada";
            notificacoes.notificar(d.getAutor().getId(), admin.getId(), TipoNotificacao.MODERACAO,
                    "Sua publicação “" + NotificacaoService.trecho(d.getTitulo())
                            + "” foi ocultada pela moderação. Motivo: " + motivo, alvoId);
        } else {
            Comentario c = comentarioRepository.buscarParaAtualizar(alvoId)
                    .orElseThrow(() -> new ModeracaoException("Comentário não encontrado."));
            if (c.isOculto()) {
                throw new ModeracaoException("O comentário já está oculto.");
            }
            c.definirOculto(true);
            detalhes = "Comentário #" + alvoId + " (publicação #" + c.getDenunciaId() + ") ocultado";
            notificarComentario(c, admin, "foi ocultado pela moderação. Motivo: " + motivo);
        }

        // Grava a ocultação e encerra todas as sinalizações abertas deste conteúdo
        sinalizacaoRepository.resolverAbertas(StatusSinalizacao.PROCEDENTE, admin.getId(),
                LocalDateTime.now(), motivo, tipo, alvoId, StatusSinalizacao.ABERTA);

        auditoria.registrar(email, AcaoAuditoria.CONTEUDO_OCULTADO, tipo.name(), alvoId, motivo, detalhes);
    }

    private void notificarComentario(Comentario c, User admin, String complemento) {
        String titulo = denunciaRepository.findById(c.getDenunciaId()).map(Denuncia::getTitulo).orElse("");
        notificacoes.notificar(c.getAutor().getId(), admin.getId(), TipoNotificacao.MODERACAO,
                "Seu comentário na ocorrência “" + NotificacaoService.trecho(titulo) + "” " + complemento,
                c.getDenunciaId());
    }

    private User admin(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }

    private static void exigirTipo(TipoConteudo tipo) {
        if (tipo == null) {
            throw new ModeracaoException("Tipo de conteúdo inválido.");
        }
    }

    private static String justificativaObrigatoria(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ModeracaoException("Informe a justificativa.");
        }
        String limpo = texto.trim();
        if (limpo.length() > MAX_JUSTIFICATIVA) {
            throw new ModeracaoException(
                    "A justificativa deve ter no máximo " + MAX_JUSTIFICATIVA + " caracteres.");
        }
        return limpo;
    }

    private static String cortar(String texto) {
        if (texto == null) return "";
        return texto.length() > MAX_TRECHO ? texto.substring(0, MAX_TRECHO) + "…" : texto;
    }
}