package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class DenunciaTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private HistoricoStatusRepository historicoRepository;
    @Autowired private CategoriaRepository categoriaRepository;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String criarAutor(String email) {
        userRepository.saveAndFlush(new User("Autor Teste", email, "hash-qualquer", Role.CIDADAO));
        return email;
    }

    private Long categoriaValida() {
        return categoriaRepository.findByAtivaTrueOrderByNome().get(0).getId();
    }

    private ResultActions postar(String email, String titulo, Object categoriaId) throws Exception {
        return mvc().perform(post("/denuncias/nova")
                .with(user(email).roles("CIDADAO"))
                .with(csrf())
                .param("titulo", titulo)
                .param("descricao", "Descrição suficientemente longa do problema.")
                .param("categoriaId", String.valueOf(categoriaId))
                .param("bairro", "Boa Vista")
                .param("regiao", "RPA 1 - Centro")
                .param("prioridadeSugerida", "ALTA"));
    }

    @Test
    void visitantePodeVerListagemEDetalheInexistenteDa404() throws Exception {
        mvc().perform(get("/denuncias")).andExpect(status().isOk());
        mvc().perform(get("/denuncias/999999")).andExpect(status().isNotFound());
    }

    @Test
    void visitanteNaoAcessaFormularioDeNovaOcorrencia() throws Exception {
        mvc().perform(get("/denuncias/nova"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void registroSemTokenCsrfEhRejeitado() throws Exception {
        String email = criarAutor("csrf.den@teste.com");
        mvc().perform(post("/denuncias/nova")
                        .with(user(email).roles("CIDADAO"))
                        .param("titulo", "Sem csrf"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cidadaoRegistraOcorrenciaComStatusRecebidaEHistorico() throws Exception {
        String email = criarAutor("autor.ok@teste.com");

        postar(email, "Buraco na Rua da Aurora", categoriaValida())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/denuncias/*"));

        Denuncia d = denunciaRepository.findAll().stream()
                .filter(x -> x.getTitulo().equals("Buraco na Rua da Aurora"))
                .findFirst().orElseThrow();

        assertThat(d.getStatus()).isEqualTo(StatusDenuncia.RECEBIDA);
        assertThat(d.getPrioridadeSugerida()).isEqualTo(Prioridade.ALTA);
        assertThat(d.getPrioridadeOficial()).isNull();

        List<HistoricoStatus> historico =
                historicoRepository.findByDenunciaIdOrderByDataAlteracaoAsc(d.getId());
        assertThat(historico).hasSize(1);
        assertThat(historico.get(0).getStatusAnterior()).isNull();
        assertThat(historico.get(0).getStatusNovo()).isEqualTo(StatusDenuncia.RECEBIDA);
    }

    @Test
    void cidadaoNaoConsegueDefinirStatusNemPrioridadeOficial() throws Exception {
        String email = criarAutor("autor.mal@teste.com");

        mvc().perform(post("/denuncias/nova")
                        .with(user(email).roles("CIDADAO"))
                        .with(csrf())
                        .param("titulo", "Tentativa de burlar")
                        .param("descricao", "Descrição suficientemente longa do problema.")
                        .param("categoriaId", String.valueOf(categoriaValida()))
                        .param("bairro", "Boa Vista")
                        .param("regiao", "RPA 1 - Centro")
                        .param("prioridadeSugerida", "BAIXA")
                        .param("status", "RESOLVIDA")
                        .param("prioridadeOficial", "URGENTE"))
                .andExpect(status().is3xxRedirection());

        Denuncia d = denunciaRepository.findAll().stream()
                .filter(x -> x.getTitulo().equals("Tentativa de burlar"))
                .findFirst().orElseThrow();

        assertThat(d.getStatus()).isEqualTo(StatusDenuncia.RECEBIDA);
        assertThat(d.getPrioridadeOficial()).isNull();
    }

    @Test
    void dadosInvalidosNaoCriamOcorrencia() throws Exception {
        String email = criarAutor("autor.inv@teste.com");
        long antes = denunciaRepository.count();

        postar(email, "", categoriaValida())
                .andExpect(status().isOk())
                .andExpect(view().name("denuncia-nova"))
                .andExpect(model().attributeHasFieldErrors("denunciaForm", "titulo"));

        assertThat(denunciaRepository.count()).isEqualTo(antes);
    }

    @Test
    void categoriaInexistenteEhRejeitada() throws Exception {
        String email = criarAutor("autor.cat@teste.com");
        long antes = denunciaRepository.count();

        postar(email, "Categoria falsa", 999999)
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("denunciaForm", "categoriaId"));

        assertThat(denunciaRepository.count()).isEqualTo(antes);
    }
}