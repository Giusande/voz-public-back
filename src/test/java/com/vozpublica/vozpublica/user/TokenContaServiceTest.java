package com.vozpublica.vozpublica.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TokenContaServiceTest {

    @Autowired private TokenContaService service;
    @Autowired private TokenContaRepository repository;
    @Autowired private UserRepository userRepository;

    private User usuario() {
        return userRepository.saveAndFlush(new User("Token", "tok." + UUID.randomUUID() + "@teste.com",
                "hash", Role.CIDADAO));
    }

    @Test
    void guardaSoOHashENuncaOTokenEmTexto() {
        User u = usuario();

        String bruto = service.emitir(u.getId(), TipoToken.REDEFINICAO_SENHA);

        assertThat(bruto).hasSize(43);
        assertThat(repository.findByTokenHash(TokenContaService.hash(bruto))).isPresent();
        assertThat(repository.findAll()).noneMatch(t -> t.getTokenHash().equals(bruto));
    }

    @Test
    void tokenSoPodeSerUsadoUmaVez() {
        User u = usuario();
        String bruto = service.emitir(u.getId(), TipoToken.REDEFINICAO_SENHA);

        assertThat(service.consumir(bruto, TipoToken.REDEFINICAO_SENHA)).contains(u.getId());
        assertThat(service.consumir(bruto, TipoToken.REDEFINICAO_SENHA)).isEmpty();
    }

    @Test
    void tokenDeOutroTipoEhRecusado() {
        User u = usuario();
        String bruto = service.emitir(u.getId(), TipoToken.VERIFICACAO_EMAIL);

        assertThat(service.valido(bruto, TipoToken.REDEFINICAO_SENHA)).isFalse();
        assertThat(service.consumir(bruto, TipoToken.REDEFINICAO_SENHA)).isEmpty();
        assertThat(service.valido(bruto, TipoToken.VERIFICACAO_EMAIL)).isTrue();
    }

    @Test
    void tokenExpiradoEhRecusado() {
        User u = usuario();
        String bruto = "A".repeat(43);
        LocalDateTime agora = LocalDateTime.now();
        repository.saveAndFlush(new TokenConta(u.getId(), TipoToken.REDEFINICAO_SENHA,
                TokenContaService.hash(bruto), agora.minusHours(2), agora.minusHours(1)));

        assertThat(service.valido(bruto, TipoToken.REDEFINICAO_SENHA)).isFalse();
        assertThat(service.consumir(bruto, TipoToken.REDEFINICAO_SENHA)).isEmpty();
    }

    @Test
    void novoTokenInvalidaOAnteriorDoMesmoTipo() {
        User u = usuario();
        String antigo = service.emitir(u.getId(), TipoToken.REDEFINICAO_SENHA);
        String novo = service.emitir(u.getId(), TipoToken.REDEFINICAO_SENHA);

        assertThat(service.valido(antigo, TipoToken.REDEFINICAO_SENHA)).isFalse();
        assertThat(service.valido(novo, TipoToken.REDEFINICAO_SENHA)).isTrue();
    }

    @Test
    void emitirUmTipoNaoInvalidaOOutro() {
        User u = usuario();
        String verificacao = service.emitir(u.getId(), TipoToken.VERIFICACAO_EMAIL);
        service.emitir(u.getId(), TipoToken.REDEFINICAO_SENHA);

        assertThat(service.valido(verificacao, TipoToken.VERIFICACAO_EMAIL)).isTrue();
    }

    @Test
    void formatoInvalidoOuInexistenteEhRecusado() {
        assertThat(service.consumir(null, TipoToken.REDEFINICAO_SENHA)).isEmpty();
        assertThat(service.consumir("abc", TipoToken.REDEFINICAO_SENHA)).isEmpty();
        assertThat(service.consumir("a".repeat(43), TipoToken.REDEFINICAO_SENHA)).isEmpty();
        assertThat(service.valido("<script>", TipoToken.REDEFINICAO_SENHA)).isFalse();
    }

    @Test
    void consultarNaoConsome() {
        User u = usuario();
        String bruto = service.emitir(u.getId(), TipoToken.REDEFINICAO_SENHA);

        assertThat(service.valido(bruto, TipoToken.REDEFINICAO_SENHA)).isTrue();
        assertThat(service.valido(bruto, TipoToken.REDEFINICAO_SENHA)).isTrue();
        assertThat(service.consumir(bruto, TipoToken.REDEFINICAO_SENHA)).isPresent();
    }
}