package com.vozpublica.vozpublica.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class TokenContaService {

    public static final Duration VALIDADE_VERIFICACAO = Duration.ofHours(24);
    public static final Duration VALIDADE_REDEFINICAO = Duration.ofHours(1);

    private static final SecureRandom ALEATORIO = new SecureRandom();
    /** 32 bytes em Base64 URL, sem preenchimento, dão 43 caracteres. */
    private static final Pattern FORMATO = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final TokenContaRepository repository;
    private final Clock clock;

    public TokenContaService(TokenContaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Devolve o token em texto (para o e-mail). No banco fica só o hash. Invalida os pendentes do mesmo tipo. */
    @Transactional
    public String emitir(Long usuarioId, TipoToken tipo) {
        LocalDateTime agora = LocalDateTime.now(clock);
        repository.invalidarPendentes(usuarioId, tipo, agora);

        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String bruto = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.save(new TokenConta(usuarioId, tipo, hash(bruto), agora, agora.plus(validade(tipo))));
        return bruto;
    }

    /** Usa o token (uma única vez). Devolve o id do usuário, ou vazio se for inválido, usado ou expirado. */
    @Transactional
    public Optional<Long> consumir(String bruto, TipoToken tipo) {
        if (!formatoValido(bruto)) return Optional.empty();
        String h = hash(bruto);
        Optional<Long> usuarioId = repository.findByTokenHash(h)
                .filter(t -> t.getTipo() == tipo).map(TokenConta::getUsuarioId);
        if (usuarioId.isEmpty()) return Optional.empty();
        if (repository.consumir(h, tipo, LocalDateTime.now(clock)) == 0) return Optional.empty();
        return usuarioId;
    }

    /** Só consulta: não consome. Usado para decidir o que a página mostra. */
    @Transactional(readOnly = true)
    public boolean valido(String bruto, TipoToken tipo) {
        if (!formatoValido(bruto)) return false;
        LocalDateTime agora = LocalDateTime.now(clock);
        return repository.findByTokenHash(hash(bruto))
                .filter(t -> t.getTipo() == tipo && t.getUsadoEm() == null && t.getExpiraEm().isAfter(agora))
                .isPresent();
    }

    @Transactional
    public void invalidarPendentes(Long usuarioId, TipoToken tipo) {
        repository.invalidarPendentes(usuarioId, tipo, LocalDateTime.now(clock));
    }

    public static String hash(String bruto) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bruto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean formatoValido(String bruto) {
        return bruto != null && FORMATO.matcher(bruto).matches();
    }

    private static Duration validade(TipoToken tipo) {
        return tipo == TipoToken.VERIFICACAO_EMAIL ? VALIDADE_VERIFICACAO : VALIDADE_REDEFINICAO;
    }
}