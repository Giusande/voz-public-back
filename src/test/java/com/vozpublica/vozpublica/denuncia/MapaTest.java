package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class MapaTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User autor() {
        return userRepository.saveAndFlush(new User("Autor Mapa", "mapa." + UUID.randomUUID() + "@teste.com",
                "hash", Role.CIDADAO));
    }

    private Denuncia denuncia(User autor, String titulo, String bairro, BigDecimal lat, BigDecimal lon) {
        return denunciaRepository.saveAndFlush(new Denuncia(autor,
                categoriaRepository.findByAtivaTrueOrderByNome().get(0), titulo,
                "Descrição de teste do problema.", null, bairro, "RPA 1 - Centro", lat, lon, Prioridade.MEDIA));
    }

    private String json(String bairro) throws Exception {
        em.flush();
        em.clear();
        return mvc().perform(get("/mapa/dados").param("bairro", bairro))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void paginaEDadosSaoPublicos() throws Exception {
        mvc().perform(get("/mapa")).andExpect(status().isOk()).andExpect(view().name("mapa"));
        mvc().perform(get("/mapa/dados")).andExpect(status().isOk());
    }

    @Test
    void soAsOcorrenciasComCoordenadasApareceme() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        User a = autor();
        denuncia(a, "Com ponto", bairro, new BigDecimal("-8.05000000"), new BigDecimal("-34.88000000"));
        denuncia(a, "Sem ponto", bairro, null, null);

        String corpo = json(bairro);

        assertThat(corpo).contains("Com ponto").doesNotContain("Sem ponto");
        assertThat(corpo).contains("-8.05").contains("-34.88");
    }

    @Test
    void ocultasEDuplicadasFicamFora() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        User a = autor();
        BigDecimal lat = new BigDecimal("-8.05000000"), lon = new BigDecimal("-34.88000000");
        Denuncia principal = denuncia(a, "Principal", bairro, lat, lon);
        Denuncia oculta = denuncia(a, "Oculta", bairro, lat, lon);
        Denuncia duplicada = denuncia(a, "Duplicada", bairro, lat, lon);
        jdbc.update("UPDATE denuncias SET oculta = TRUE WHERE id = ?", oculta.getId());
        jdbc.update("UPDATE denuncias SET duplicada_de_id = ? WHERE id = ?", principal.getId(), duplicada.getId());

        String corpo = json(bairro);

        assertThat(corpo).contains("Principal").doesNotContain("Oculta").doesNotContain("Duplicada");
    }

    @Test
    void filtrosFuncionamEValoresInvalidosSaoIgnorados() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        User a = autor();
        Denuncia d = denuncia(a, "Filtrada", bairro, new BigDecimal("-8.05"), new BigDecimal("-34.88"));
        jdbc.update("UPDATE denuncias SET status = 'EM_EXECUCAO' WHERE id = ?", d.getId());
        em.flush();
        em.clear();

        mvc().perform(get("/mapa/dados").param("bairro", bairro).param("status", "EM_EXECUCAO"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Filtrada")));
        mvc().perform(get("/mapa/dados").param("bairro", bairro).param("status", "RESOLVIDA"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Filtrada"))));
        mvc().perform(get("/mapa/dados").param("status", "XYZ").param("categoriaId", "abc"))
                .andExpect(status().isOk());
    }

    @Test
    void dadosNaoExpoemOAutor() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        User a = autor();
        denuncia(a, "Sem autor no json", bairro, new BigDecimal("-8.05"), new BigDecimal("-34.88"));

        String corpo = json(bairro);

        assertThat(corpo).doesNotContain(a.getEmail()).doesNotContain(a.getName());
    }

    @Test
    void paginaTemAtribuicaoDoOpenStreetMapEDoLeaflet() throws Exception {
        String html = mvc().perform(get("/mapa")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("OpenStreetMap").contains("leaflet@1.9.4");
    }
}