package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.categoria.Categoria;
import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.DenunciaConsultaService.Contadores;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class DenunciaBuscaTest {

    @Autowired private DenunciaConsultaService consulta;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WebApplicationContext context;
    @PersistenceContext private EntityManager em;

    private User autor;
    private String marca; // bairro único deste teste

    @BeforeEach
    void preparar() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        marca = "Bairro-" + sufixo;
        autor = novoUsuario("busca." + sufixo + "@teste.com");
    }

    private User novoUsuario(String email) {
        return userRepository.saveAndFlush(new User("Teste", email, "hash", Role.CIDADAO));
    }

    private Categoria categoria() {
        return categoriaRepository.findByAtivaTrueOrderByNome().get(0);
    }

    private Denuncia criar(String titulo, Prioridade sugerida) {
        return denunciaRepository.saveAndFlush(new Denuncia(autor, categoria(), titulo,
                "Descrição de teste do problema.", null, marca, "RPA 1 - Centro",
                null, null, sugerida));
    }

    private FiltroDenuncia filtro() {
        FiltroDenuncia f = new FiltroDenuncia();
        f.setBairro(marca);
        return f;
    }

    private List<String> titulos(FiltroDenuncia f) {
        em.flush();
        em.clear();
        return consulta.buscar(f, 0).page().getContent().stream().map(Denuncia::getTitulo).toList();
    }

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void filtraPorBairroIgnorandoMaiusculas() {
        criar("A", Prioridade.MEDIA);
        FiltroDenuncia f = new FiltroDenuncia();
        f.setBairro(marca.toUpperCase());
        assertThat(titulos(f)).containsExactly("A");

        f.setBairro("inexistente-" + UUID.randomUUID());
        assertThat(titulos(f)).isEmpty();
    }

    @Test
    void percentualDigitadoNaoViraCuringa() {
        criar("A", Prioridade.MEDIA);
        FiltroDenuncia f = new FiltroDenuncia();
        f.setBairro("%");
        em.flush();
        em.clear();
        assertThat(consulta.buscar(f, 0).page().getTotalElements()).isZero();
    }

    @Test
    void filtraPorStatus() {
        Denuncia d1 = criar("Recebida", Prioridade.MEDIA);
        Denuncia d2 = criar("Em execução", Prioridade.MEDIA);
        jdbc.update("UPDATE denuncias SET status = 'EM_EXECUCAO' WHERE id = ?", d2.getId());

        FiltroDenuncia f = filtro();
        f.setStatus(StatusDenuncia.EM_EXECUCAO);
        assertThat(titulos(f)).containsExactly("Em execução");
        assertThat(d1.getId()).isNotNull();
    }

    @Test
    void filtroDePrioridadeUsaAOficialQuandoExiste() {
        Denuncia d1 = criar("Sugerida baixa, oficial urgente", Prioridade.BAIXA);
        Denuncia d2 = criar("Sugerida urgente, oficial baixa", Prioridade.URGENTE);
        jdbc.update("UPDATE denuncias SET prioridade_oficial = 'URGENTE' WHERE id = ?", d1.getId());
        jdbc.update("UPDATE denuncias SET prioridade_oficial = 'BAIXA' WHERE id = ?", d2.getId());

        FiltroDenuncia f = filtro();
        f.setPrioridade(Prioridade.URGENTE);
        assertThat(titulos(f)).containsExactly("Sugerida baixa, oficial urgente");
    }

    @Test
    void filtraPorPeriodoComLimitesInclusivos() {
        Denuncia d1 = criar("Janeiro", Prioridade.MEDIA);
        Denuncia d2 = criar("Março", Prioridade.MEDIA);
        jdbc.update("UPDATE denuncias SET data_criacao = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2020, 1, 10, 12, 0)), d1.getId());
        jdbc.update("UPDATE denuncias SET data_criacao = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2020, 3, 10, 12, 0)), d2.getId());

        FiltroDenuncia f = filtro();
        f.setDe(LocalDate.of(2020, 1, 1));
        f.setAte(LocalDate.of(2020, 1, 10)); // o último dia conta inteiro
        assertThat(titulos(f)).containsExactly("Janeiro");

        f.setDe(LocalDate.of(2020, 2, 1));
        f.setAte(LocalDate.of(2020, 12, 31));
        assertThat(titulos(f)).containsExactly("Março");
    }

    @Test
    void ordenaPorMaisIncentivadas() {
        Denuncia d1 = criar("Zero incentivos", Prioridade.MEDIA);
        Denuncia d2 = criar("Dois incentivos", Prioridade.MEDIA);
        Denuncia d3 = criar("Um incentivo", Prioridade.MEDIA);
        User u1 = novoUsuario("inc1." + UUID.randomUUID() + "@teste.com");
        User u2 = novoUsuario("inc2." + UUID.randomUUID() + "@teste.com");
        String sql = "INSERT INTO incentivos (denuncia_id, usuario_id) VALUES (?, ?)";
        jdbc.update(sql, d2.getId(), u1.getId());
        jdbc.update(sql, d2.getId(), u2.getId());
        jdbc.update(sql, d3.getId(), u1.getId());

        FiltroDenuncia f = filtro();
        f.setOrdem(FiltroDenuncia.Ordem.INCENTIVADAS);
        assertThat(titulos(f)).containsExactly("Dois incentivos", "Um incentivo", "Zero incentivos");
        assertThat(d1.getId()).isNotNull();
    }

    @Test
    void ordenaPorMaisUrgentesConsiderandoAOficial() {
        criar("Baixa", Prioridade.BAIXA);
        criar("Media", Prioridade.MEDIA);
        criar("Alta", Prioridade.ALTA);
        Denuncia oficial = criar("Baixa com oficial urgente", Prioridade.BAIXA);
        jdbc.update("UPDATE denuncias SET prioridade_oficial = 'URGENTE' WHERE id = ?", oficial.getId());

        FiltroDenuncia f = filtro();
        f.setOrdem(FiltroDenuncia.Ordem.URGENTES);
        assertThat(titulos(f)).containsExactly(
                "Baixa com oficial urgente", "Alta", "Media", "Baixa");
    }

    @Test
    void contadoresVemAgrupadosPorOcorrencia() {
        Denuncia d1 = criar("Com interações", Prioridade.MEDIA);
        Denuncia d2 = criar("Sem interações", Prioridade.MEDIA);
        User u1 = novoUsuario("c1." + UUID.randomUUID() + "@teste.com");
        User u2 = novoUsuario("c2." + UUID.randomUUID() + "@teste.com");
        jdbc.update("INSERT INTO curtidas (denuncia_id, usuario_id) VALUES (?, ?)", d1.getId(), u1.getId());
        jdbc.update("INSERT INTO curtidas (denuncia_id, usuario_id) VALUES (?, ?)", d1.getId(), u2.getId());
        jdbc.update("INSERT INTO incentivos (denuncia_id, usuario_id) VALUES (?, ?)", d1.getId(), u1.getId());
        jdbc.update("INSERT INTO comentarios (denuncia_id, usuario_id, conteudo) VALUES (?, ?, 'oi')",
                d1.getId(), u2.getId());

        em.flush();
        em.clear();
        var resultado = consulta.buscar(filtro(), 0);

        assertThat(resultado.contadores().get(d1.getId())).isEqualTo(new Contadores(2, 1, 1));
        assertThat(resultado.contadores().get(d2.getId())).isEqualTo(new Contadores(0, 0, 0));
    }

    @Test
    void listagemPublicaIgnoraFiltrosInvalidos() throws Exception {
        mvc().perform(get("/denuncias")
                        .param("ordem", "XYZ")
                        .param("categoriaId", "abc")
                        .param("de", "2020-13-45")
                        .param("pagina", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("contadores"));
    }
}