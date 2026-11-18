package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class DenunciaNovaMapaTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void formularioTemOMapaParaEscolherOPonto() throws Exception {
        String html = mvc().perform(get("/denuncias/nova").with(user("qualquer@teste.com").roles("CIDADAO")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("id=\"mapa-escolha\"").contains("leaflet@1.9.4")
                .contains("OpenStreetMap").contains("id=\"latitude\"").contains("id=\"longitude\"");
    }

    @Test
    void registroGravaAsCoordenadasEscolhidasNoMapa() throws Exception {
        User autor = userRepository.saveAndFlush(new User("Autor Mapa",
                "pick." + UUID.randomUUID() + "@teste.com", "hash", Role.CIDADAO));
        String titulo = "Com ponto " + UUID.randomUUID();

        mvc().perform(post("/denuncias/nova").with(user(autor.getEmail()).roles("CIDADAO")).with(csrf())
                        .param("titulo", titulo)
                        .param("descricao", "Descrição suficientemente longa do problema.")
                        .param("categoriaId", String.valueOf(categoriaRepository.findByAtivaTrueOrderByNome().get(0).getId()))
                        .param("bairro", "Boa Vista").param("regiao", "RPA 1 - Centro")
                        .param("prioridadeSugerida", "MEDIA")
                        .param("latitude", "-8.063100").param("longitude", "-34.871100"))
                .andExpect(status().is3xxRedirection());

        Denuncia d = denunciaRepository.findAll().stream()
                .filter(x -> x.getTitulo().equals(titulo)).findFirst().orElseThrow();
        assertThat(d.getLatitude()).isEqualByComparingTo(new BigDecimal("-8.0631"));
        assertThat(d.getLongitude()).isEqualByComparingTo(new BigDecimal("-34.8711"));
    }
}