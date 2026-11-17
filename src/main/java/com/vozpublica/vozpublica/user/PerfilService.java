package com.vozpublica.vozpublica.user;

import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaConsultaService;
import com.vozpublica.vozpublica.denuncia.DenunciaPerfilRepository;
import com.vozpublica.vozpublica.interacao.ApoioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PerfilService {

    private static final int TAMANHO_PAGINA = 10;

    /** membroDesde e apoios são nulos quando o cidadão não autorizou a exibição (e quem vê não é o dono). */
    public record Publico(User usuario, boolean dono, LocalDateTime membroDesde, Long apoios,
                          DenunciaConsultaService.Resultado publicacoes) {}

    private final UserRepository userRepository;
    private final DenunciaPerfilRepository perfilRepository;
    private final DenunciaConsultaService consultaService;
    private final ApoioRepository apoioRepository;

    public PerfilService(UserRepository userRepository, DenunciaPerfilRepository perfilRepository,
                         DenunciaConsultaService consultaService, ApoioRepository apoioRepository) {
        this.userRepository = userRepository;
        this.perfilRepository = perfilRepository;
        this.consultaService = consultaService;
        this.apoioRepository = apoioRepository;
    }

    /** Vazio = 404: não existe, está suspenso, ou é privado e quem pede não é o dono. */
    @Transactional(readOnly = true)
    public Optional<Publico> carregar(Long id, String emailOuNull, int pagina) {
        Optional<User> encontrado = userRepository.findById(id).filter(User::isEnabled);
        if (encontrado.isEmpty()) {
            return Optional.empty();
        }
        User u = encontrado.get();
        boolean dono = emailOuNull != null && u.getEmail().equalsIgnoreCase(emailOuNull.trim());
        if (!u.isPerfilPublico() && !dono) {
            return Optional.empty();
        }

        Page<Denuncia> page = perfilRepository
                .findByAutorIdAndOcultaFalseAndDuplicadaDeIdIsNullOrderByDataCriacaoDesc(
                        id, PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA));

        LocalDateTime membroDesde = (u.isExibirDataCadastro() || dono) ? u.getCreatedAt() : null;
        Long apoios = (u.isExibirApoios() || dono) ? apoioRepository.contarVisiveis(id) : null;

        return Optional.of(new Publico(u, dono, membroDesde, apoios, consultaService.complementar(page)));
    }

    @Transactional(readOnly = true)
    public Long idDe(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }

    @Transactional(readOnly = true)
    public ConfigPerfilForm formularioDe(String email) {
        User u = usuario(email);
        ConfigPerfilForm form = new ConfigPerfilForm();
        form.setNomePublico(u.getNomePublico());
        form.setPerfilPublico(u.isPerfilPublico());
        form.setExibirDataCadastro(u.isExibirDataCadastro());
        form.setExibirApoios(u.isExibirApoios());
        return form;
    }

    /** Sempre altera a conta de quem está logado: o alvo nunca vem da requisição. */
    @Transactional
    public void atualizarPrivacidade(String email, ConfigPerfilForm form) {
        User u = usuario(email);
        String nome = form.getNomePublico() == null ? null : form.getNomePublico().strip();
        if (nome != null && nome.isEmpty()) {
            nome = null;
        }
        u.atualizarPrivacidade(nome, form.isPerfilPublico(), form.isExibirDataCadastro(), form.isExibirApoios());
    }

    private User usuario(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }
}