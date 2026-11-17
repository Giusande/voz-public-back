package com.vozpublica.vozpublica.interacao;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.denuncia.Prioridade;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class InteracaoTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private CurtidaRepository curtidaRepository;
    @Autowired private IncentivoRepository incentivoRepository;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private User criarUsuario(String email) {
        return userRepository.saveAndFlush(new User("Teste", email, "hash", Role.CIDADAO));
    }

    private Denuncia criarDenuncia(User autor) {
        var categoria = categoriaRepository.findByAtivaTrueOrderByNome().get(0);
        return denunciaRepository.saveAndFlush(new Denuncia(autor, categoria,
                "Ocorrência de teste", "Descrição de teste do problema.",
                null, "Boa Vista", "RPA 1 - Centro", null, null, Prioridade.MEDIA));
    }

    @Test
    void visitanteNaoCurteNemIncentiva() throws Exception {
        Denuncia d = criarDenuncia(criarUsuario("autor.v@teste.com"));

        mvc().perform(post("/denuncias/{id}/curtir", d.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc().perform(post("/denuncias/{id}/incentivar", d.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        assertThat(curtidaRepository.countByDenunciaId(d.getId())).isZero();
        assertThat(incentivoRepository.countByDenunciaId(d.getId())).isZero();
    }

    @Test
    void interacaoSemTokenCsrfEhRejeitada() throws Exception {
        criarUsuario("csrf.i@teste.com");
        Denuncia d = criarDenuncia(criarUsuario("autor.c@teste.com"));

        mvc().perform(post("/denuncias/{id}/incentivar", d.getId())
                        .with(user("csrf.i@teste.com").roles("CIDADAO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void curtirAlternaEntreCurtidoEDescurtido() throws Exception {
        criarUsuario("curte@teste.com");
        Denuncia d = criarDenuncia(criarUsuario("autor.l@teste.com"));

        mvc().perform(post("/denuncias/{id}/curtir", d.getId())
                .with(user("curte@teste.com").roles("CIDADAO")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(curtidaRepository.countByDenunciaId(d.getId())).isEqualTo(1);

        mvc().perform(post("/denuncias/{id}/curtir", d.getId())
                .with(user("curte@teste.com").roles("CIDADAO")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(curtidaRepository.countByDenunciaId(d.getId())).isZero();
    }

    @Test
    void incentivoContaApenasUmaVezPorUsuario() throws Exception {
        criarUsuario("incentiva@teste.com");
        Denuncia d = criarDenuncia(criarUsuario("autor.i@teste.com"));

        for (int i = 0; i < 3; i++) {
            mvc().perform(post("/denuncias/{id}/incentivar", d.getId())
                    .with(user("incentiva@teste.com").roles("CIDADAO")).with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }
        assertThat(incentivoRepository.countByDenunciaId(d.getId())).isEqualTo(1);
    }

    @Test
    void usuariosDiferentesPodemIncentivarAMesmaOcorrencia() throws Exception {
        criarUsuario("a1@teste.com");
        criarUsuario("a2@teste.com");
        Denuncia d = criarDenuncia(criarUsuario("autor.m@teste.com"));

        for (String email : new String[]{"a1@teste.com", "a2@teste.com"}) {
            mvc().perform(post("/denuncias/{id}/incentivar", d.getId())
                    .with(user(email).roles("CIDADAO")).with(csrf()));
        }
        assertThat(incentivoRepository.countByDenunciaId(d.getId())).isEqualTo(2);
    }

    @Test
    void ocorrenciaInexistenteDa404() throws Exception {
        criarUsuario("naoexiste@teste.com");

        mvc().perform(post("/denuncias/999999/incentivar")
                        .with(user("naoexiste@teste.com").roles("CIDADAO")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void detalheMostraResumoParaVisitante() throws Exception {
        Denuncia d = criarDenuncia(criarUsuario("autor.d@teste.com"));

        mvc().perform(get("/denuncias/{id}", d.getId()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("resumo"))
                .andExpect(model().attribute("autenticado", false));
    }
}