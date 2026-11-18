package com.vozpublica.vozpublica.user;

import com.vozpublica.vozpublica.security.LimitadorTentativas;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Service
public class ContaRecuperacaoService {

    /** O que o chamador deve enviar por e-mail, depois de a transação terminar. */
    public record Envio(String destino, String nome, String token) {}

    private static final Duration HORA = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final TokenContaService tokens;
    private final PasswordEncoder passwordEncoder;
    private final LimitadorTentativas limitador;
    private final ContaProps props;

    public ContaRecuperacaoService(UserRepository userRepository, TokenContaService tokens,
                                   PasswordEncoder passwordEncoder, LimitadorTentativas limitador,
                                   ContaProps props) {
        this.userRepository = userRepository;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.limitador = limitador;
        this.props = props;
    }

    /** Vazio em qualquer caso de "não enviar": o chamador responde sempre igual, para não revelar nada. */
    @Transactional
    public Optional<Envio> solicitarRecuperacao(String emailBruto, String ip) {
        String email = normalizar(emailBruto);
        boolean okIp = limitador.permitir("rec-ip:" + ip, 10, HORA);
        boolean okEmail = email != null && limitador.permitir("rec-email:" + email, 3, HORA);
        if (email == null || !okIp || !okEmail) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email).filter(User::isEnabled)
                .map(u -> new Envio(u.getEmail(), u.getName(),
                        tokens.emitir(u.getId(), TipoToken.REDEFINICAO_SENHA)));
    }

    @Transactional(readOnly = true)
    public boolean tokenRedefinicaoValido(String token) {
        return tokens.valido(token, TipoToken.REDEFINICAO_SENHA);
    }

    /** A senha já deve ter passado na validação do formulário: assim um erro de digitação não gasta o token. */
    @Transactional
    public boolean redefinirSenha(String token, String novaSenha, String ip) {
        if (novaSenha == null || novaSenha.length() < 8 || novaSenha.length() > 72) {
            throw new ContaInvalidaException("A senha deve ter entre 8 e 72 caracteres.");
        }
        if (!limitador.permitir("redef-ip:" + ip, 20, HORA)) {
            return false;
        }
        Optional<Long> usuarioId = tokens.consumir(token, TipoToken.REDEFINICAO_SENHA);
        if (usuarioId.isEmpty()) {
            return false;
        }
        Optional<User> alvo = userRepository.buscarParaAtualizar(usuarioId.get()).filter(User::isEnabled);
        if (alvo.isEmpty()) {
            return false;
        }
        User u = alvo.get();
        u.trocarSenha(passwordEncoder.encode(novaSenha));
        u.marcarEmailVerificado(); // quem recebeu o link prova que controla o e-mail
        tokens.invalidarPendentes(u.getId(), TipoToken.REDEFINICAO_SENHA);
        return true;
    }

    /** Token para a conta que acabou de ser criada (nada a fazer se a verificação não for obrigatória). */
    @Transactional
    public Optional<Envio> verificacaoParaNovaConta(User usuario) {
        if (!props.isVerificacaoObrigatoria() || usuario.isEmailVerificado()) {
            return Optional.empty();
        }
        return Optional.of(new Envio(usuario.getEmail(), usuario.getName(),
                tokens.emitir(usuario.getId(), TipoToken.VERIFICACAO_EMAIL)));
    }

    @Transactional
    public Optional<Envio> reenviarVerificacao(String emailBruto, String ip) {
        String email = normalizar(emailBruto);
        boolean okIp = limitador.permitir("ver-ip:" + ip, 10, HORA);
        boolean okEmail = email != null && limitador.permitir("ver-email:" + email, 3, HORA);
        if (email == null || !okIp || !okEmail) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email)
                .filter(u -> u.isEnabled() && !u.isEmailVerificado())
                .map(u -> new Envio(u.getEmail(), u.getName(),
                        tokens.emitir(u.getId(), TipoToken.VERIFICACAO_EMAIL)));
    }

    @Transactional(readOnly = true)
    public boolean tokenVerificacaoValido(String token) {
        return tokens.valido(token, TipoToken.VERIFICACAO_EMAIL);
    }

    @Transactional
    public boolean verificarEmail(String token) {
        Optional<Long> usuarioId = tokens.consumir(token, TipoToken.VERIFICACAO_EMAIL);
        if (usuarioId.isEmpty()) {
            return false;
        }
        return userRepository.buscarParaAtualizar(usuarioId.get()).map(u -> {
            u.marcarEmailVerificado();
            return true;
        }).orElse(false);
    }

    private static String normalizar(String email) {
        if (email == null) return null;
        String e = email.trim().toLowerCase(Locale.ROOT);
        return e.isEmpty() || e.length() > 150 ? null : e;
    }
}