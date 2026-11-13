package com.vozpublica.vozpublica.comentario;

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
import org.springframework.test.web.servlet.ResultActions;
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
class ComentarioTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ComentarioRepository comentarioRepository;

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

    private ResultActions comentar(String email, Long denunciaId, String texto) throws Exception {
        return mvc().perform(post("/denuncias/{id}/comentarios", denunciaId)
                .with(user(email).roles("CIDADAO")).with(csrf())
                .param("conteudo", texto));
    }

    @Test
    void visitanteNaoComenta() throws Exception {
        Denuncia d = criarDenuncia(criarUsuario("autor.cv@teste.com"));

        mvc().perform(post("/denuncias/{id}/comentarios", d.getId()).with(csrf())
                        .param("conteudo", "Olá"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        assertThat(comentarioRepository.countByDenunciaId(d.getId())).isZero();
    }

    @Test
    void comentarioSemTokenCsrfEhRejeitado() throws Exception {
        criarUsuario("csrf.c@teste.com");
        Denuncia d = criarDenuncia(criarUsuario("autor.cc@teste.com"));

        mvc().perform(post("/denuncias/{id}/comentarios", d.getId())
                        .with(user("csrf.c@teste.com").roles("CIDADAO"))
                        .param("conteudo", "Olá"))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuarioLogadoComentaEComentarioFicaVinculadoAoAutor() throws Exception {
        criarUsuario("comenta@teste.com");
        Denuncia d = criarDenuncia(criarUsuario("autor.co@teste.com"));

        comentar("comenta@teste.com", d.getId(), "  Também vejo esse problema.  ")
                .andExpect(status().is3xxRedirection());

        var lista = comentarioRepository.findByDenunciaIdOrderByDataCriacaoAsc(d.getId());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).getConteudo()).isEqualTo("Também vejo esse problema.");
        assertThat(lista.get(0).getAutor().getEmail()).isEqualTo("comenta@teste.com");
    }

    @Test
    void comentarioVazioEhRejeitado() throws Exception {
        criarUsuario("vazio@teste.com");
        Denuncia d = criarDenuncia(criarUsuario("autor.cvz@teste.com"));

        comentar("vazio@teste.com", d.getId(), "   ")
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("erroComentario"));

        assertThat(comentarioRepository.countByDenunciaId(d.getId())).isZero();
    }

    @Test
    void comentarioGrandeDemaisEhRejeitado() throws Exception {
        criarUsuario("longo@teste.com");
        Denuncia d = criarDenuncia(criarUsuario("autor.cl@teste.com"));

        comentar("longo@teste.com", d.getId(), "a".repeat(1001))
                .andExpect(flash().attributeExists("erroComentario"));

        assertThat(comentarioRepository.countByDenunciaId(d.getId())).isZero();
    }

    @Test
    void htmlNoComentarioEhExibidoEscapado() throws Exception {
        criarUsuario("xss@teste.com");
        Denuncia d = criarDenuncia(criarUsuario("autor.cx@teste.com"));

        comentar("xss@teste.com", d.getId(), "<script>alert(1)</script>")
                .andExpect(status().is3xxRedirection());

        String html = mvc().perform(get("/denuncias/{id}", d.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>alert(1)</script>");
    }

    @Test
    void comentarEmOcorrenciaInexistenteDa404() throws Exception {
        criarUsuario("inexistente@teste.com");

        comentar("inexistente@teste.com", 999999L, "Olá")
                .andExpect(status().isNotFound());
    }
}