package com.vozpublica.vozpublica.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 120)
    private String name;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "senha", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 20)
    private Role role;

    @Column(name = "ativo", nullable = false)
    private boolean enabled = true;

    @Column(name = "data_cadastro", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "nome_publico", length = 60)
    private String nomePublico;

    @Column(name = "perfil_publico", nullable = false)
    private boolean perfilPublico = true;

    @Column(name = "exibir_data_cadastro", nullable = false)
    private boolean exibirDataCadastro = false;

    @Column(name = "exibir_apoios", nullable = false)
    private boolean exibirApoios = false;

    /** Verdadeiro por padrão; o UserService marca como pendente as contas novas quando a verificação é obrigatória. */
    @Column(name = "email_verificado", nullable = false)
    private boolean emailVerificado = true;

    @Column(name = "versao_credencial", nullable = false)
    private int versaoCredencial = 0;

    protected User() {} // exigido pelo JPA

    public User(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    void alterarPapel(Role novoPapel) {
        this.role = novoPapel;
    }

    void definirAtivo(boolean ativo) {
        this.enabled = ativo;
    }

    /** Package-private: só o PerfilService altera a privacidade, e sempre da própria conta. */
    void atualizarPrivacidade(String nomePublico, boolean perfilPublico,
                              boolean exibirDataCadastro, boolean exibirApoios) {
        this.nomePublico = nomePublico;
        this.perfilPublico = perfilPublico;
        this.exibirDataCadastro = exibirDataCadastro;
        this.exibirApoios = exibirApoios;
    }

    void exigirVerificacaoEmail() {
        this.emailVerificado = false;
    }

    void marcarEmailVerificado() {
        this.emailVerificado = true;
    }

    /** Troca a senha e sobe a versão: as sessões abertas antes deixam de valer. */
    void trocarSenha(String novoHash) {
        this.passwordHash = novoHash;
        this.versaoCredencial++;
    }

    /** Nome mostrado publicamente: o nome público, se houver; senão o nome completo. */
    public String getNomeExibicao() {
        return nomePublico == null || nomePublico.isBlank() ? name : nomePublico;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getNomePublico() { return nomePublico; }
    public boolean isPerfilPublico() { return perfilPublico; }
    public boolean isExibirDataCadastro() { return exibirDataCadastro; }
    public boolean isExibirApoios() { return exibirApoios; }
    public boolean isEmailVerificado() { return emailVerificado; }
    public int getVersaoCredencial() { return versaoCredencial; }
}