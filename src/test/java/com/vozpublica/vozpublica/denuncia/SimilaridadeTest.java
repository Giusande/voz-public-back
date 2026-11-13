package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.categoria.Categoria;
import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.SimilaridadeService.Similar;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class SimilaridadeTest {

    @Autowired private SimilaridadeService similaridade;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WebApplicationContext context;
    @PersistenceContext private EntityManager em;

    private User autor;
    private String marca;
    private Categoria cat1;
    private Categoria cat2;

    @BeforeEach
    void preparar() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        marca = "Bairro-" + sufixo;
        autor = userRepository.saveAndFlush(
                new User("Teste", "sim." + sufixo + "@teste.com", "hash", Role.CIDADAO));
        List<Categoria> cats = categoriaRepository.findByAtivaTrueOrderByNome();
        cat1 = cats.get(0);
        cat2 = cats.get(1);
    }

    private Denuncia criar(String titulo, Categoria cat, String bairro, BigDecimal lat, BigDecimal lon) {
        return denunciaRepository.saveAndFlush(new Denuncia(autor, cat, titulo,
                "Descrição de teste do problema.", null, bairro, "RPA 1 - Centro",
                lat, lon, Prioridade.MEDIA));
    }

    private boolean contem(List<Similar> lista, Denuncia d) {
        return lista.stream().anyMatch(s -> s.id().equals(d.getId()));
    }

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void encontraMesmaCategoriaEBairroComPalavrasEmComum() {
        Denuncia d = criar("Buraco enorme na rua", cat1, marca, null, null);

        List<Similar> r = similaridade.buscar(cat1.getId(), marca, "Buraco na rua", null, null);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).id()).isEqualTo(d.getId());
        assertThat(r.get(0).pontos()).isEqualTo(3); // bairro (1) + "buraco" (2)
    }

    @Test
    void naoEncontraOcorrenciaDeOutraCategoria() {
        criar("Buraco enorme na rua", cat2, marca, null, null);

        assertThat(similaridade.buscar(cat1.getId(), marca, "Buraco na rua", null, null)).isEmpty();
    }

    @Test
    void naoSugereOcorrenciasJaEncerradas() {
        Denuncia d = criar("Buraco enorme na rua", cat1, marca, null, null);
        jdbc.update("UPDATE denuncias SET status = 'RESOLVIDA' WHERE id = ?", d.getId());
        em.flush();
        em.clear();

        assertThat(similaridade.buscar(cat1.getId(), marca, "Buraco na rua", null, null)).isEmpty();
    }

    @Test
    void encontraPorProximidadeMesmoComBairroDiferente() {
        Denuncia d = criar("Lâmpada queimada", cat1, marca,
                new BigDecimal("-8.05000000"), new BigDecimal("-34.88000000"));

        // ~55 m de distância, bairro digitado diferente
        List<Similar> perto = similaridade.buscar(cat1.getId(), "Outro-" + marca, "Poste apagado",
                new BigDecimal("-8.0505"), new BigDecimal("-34.8800"));
        assertThat(contem(perto, d)).isTrue();

        // ~5 km de distância
        List<Similar> longe = similaridade.buscar(cat1.getId(), "Outro-" + marca, "Poste apagado",
                new BigDecimal("-8.1000"), new BigDecimal("-34.8800"));
        assertThat(contem(longe, d)).isFalse();
    }

    @Test
    void semBairroNemCoordenadasNaoSugereNada() {
        criar("Buraco enorme na rua", cat1, marca, null, null);

        assertThat(similaridade.buscar(cat1.getId(), "  ", "Buraco na rua", null, null)).isEmpty();
        assertThat(similaridade.buscar(null, marca, "Buraco na rua", null, null)).isEmpty();
    }

    @Test
    void endpointExigeLoginEMostraSugestoes() throws Exception {
        criar("Buraco gigante na avenida", cat1, marca, null, null);

        mvc().perform(get("/denuncias/similares").param("categoriaId", String.valueOf(cat1.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        String html = mvc().perform(get("/denuncias/similares")
                        .with(user("qualquer@teste.com").roles("CIDADAO"))
                        .param("categoriaId", String.valueOf(cat1.getId()))
                        .param("bairro", marca)
                        .param("titulo", "buraco na avenida"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Buraco gigante na avenida");
    }
}