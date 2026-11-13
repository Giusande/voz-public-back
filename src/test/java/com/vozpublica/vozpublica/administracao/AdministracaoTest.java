package com.vozpublica.vozpublica.administracao;

import com.vozpublica.vozpublica.categoria.Categoria;
import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.unidade.TipoUnidade;
import com.vozpublica.vozpublica.unidade.Unidade;
import com.vozpublica.vozpublica.unidade.UnidadeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AdministracaoTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @PersistenceContext private EntityManager em;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private ResultActions criarUnidade(String nome, String tipo, Long paiId) throws Exception {
        var b = post("/admin/unidades").with(user("admin@teste.com").roles("ADMINISTRADOR")).with(csrf())
                .param("nome", nome).param("tipo", tipo);
        if (paiId != null) b.param("paiId", String.valueOf(paiId));
        return mvc().perform(b);
    }

    private boolean existe(String nome) {
        return unidadeRepository.findAll().stream().anyMatch(u -> u.getNome().equals(nome));
    }

    private Unidade buscar(String nome) {
        return unidadeRepository.findAll().stream()
                .filter(u -> u.getNome().equals(nome)).findFirst().orElseThrow();
    }

    @Test
    void soAdministradorAcessaAsTelasAdministrativas() throws Exception {
        for (String url : new String[]{"/admin/unidades", "/admin/categorias"}) {
            mvc().perform(get(url))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
            mvc().perform(get(url).with(user("c@teste.com").roles("CIDADAO")))
                    .andExpect(status().isForbidden());
            mvc().perform(get(url).with(user("s@teste.com").roles("SERVIDOR")))
                    .andExpect(status().isForbidden());
            mvc().perform(get(url).with(user("a@teste.com").roles("ADMINISTRADOR")))
                    .andExpect(status().isOk());
        }

        mvc().perform(post("/admin/unidades").with(user("s@teste.com").roles("SERVIDOR")).with(csrf())
                        .param("nome", "Tentativa").param("tipo", "SECRETARIA"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorCriaSecretariaEDepartamentoAbaixoDela() throws Exception {
        String nomeSec = "Sec-" + UUID.randomUUID();
        String nomeDep = "Dep-" + UUID.randomUUID();

        criarUnidade(nomeSec, "SECRETARIA", null).andExpect(flash().attributeExists("mensagem"));
        Unidade sec = buscar(nomeSec);
        assertThat(sec.getPai()).isNull();

        criarUnidade(nomeDep, "DEPARTAMENTO", sec.getId()).andExpect(flash().attributeExists("mensagem"));
        assertThat(buscar(nomeDep).getPai().getId()).isEqualTo(sec.getId());
    }

    @Test
    void regrasDeHierarquiaSaoAplicadas() throws Exception {
        Unidade sec = unidadeRepository.saveAndFlush(
                new Unidade("Sec-" + UUID.randomUUID(), TipoUnidade.SECRETARIA, null));
        Unidade dep = unidadeRepository.saveAndFlush(
                new Unidade("Dep-" + UUID.randomUUID(), TipoUnidade.DEPARTAMENTO, sec));
        Unidade equipe = unidadeRepository.saveAndFlush(
                new Unidade("Eq-" + UUID.randomUUID(), TipoUnidade.EQUIPE, dep));

        String semPai = "SemPai-" + UUID.randomUUID();
        criarUnidade(semPai, "DEPARTAMENTO", null).andExpect(flash().attributeExists("erro"));
        assertThat(existe(semPai)).isFalse();

        String depSobreDep = "DepSobreDep-" + UUID.randomUUID();
        criarUnidade(depSobreDep, "DEPARTAMENTO", dep.getId()).andExpect(flash().attributeExists("erro"));
        assertThat(existe(depSobreDep)).isFalse();

        String equipeSobreEquipe = "EqSobreEq-" + UUID.randomUUID();
        criarUnidade(equipeSobreEquipe, "EQUIPE", equipe.getId()).andExpect(flash().attributeExists("erro"));
        assertThat(existe(equipeSobreEquipe)).isFalse();

        // secretaria ignora a unidade superior enviada
        String secComPai = "SecComPai-" + UUID.randomUUID();
        criarUnidade(secComPai, "SECRETARIA", sec.getId()).andExpect(flash().attributeExists("mensagem"));
        assertThat(buscar(secComPai).getPai()).isNull();
    }

    @Test
    void nomeDuplicadoNoMesmoLocalEhRejeitadoIgnorandoMaiusculas() throws Exception {
        String base = UUID.randomUUID().toString();

        criarUnidade("Sec-" + base, "SECRETARIA", null).andExpect(flash().attributeExists("mensagem"));
        criarUnidade("SEC-" + base.toUpperCase(), "SECRETARIA", null)
                .andExpect(flash().attributeExists("erro"));

        long quantas = unidadeRepository.findAll().stream()
                .filter(u -> u.getNome().equalsIgnoreCase("Sec-" + base)).count();
        assertThat(quantas).isEqualTo(1);
    }

    @Test
    void administradorDesativaEReativaUnidade() throws Exception {
        Unidade sec = unidadeRepository.saveAndFlush(
                new Unidade("Sec-" + UUID.randomUUID(), TipoUnidade.SECRETARIA, null));
        assertThat(sec.isAtiva()).isTrue();

        mvc().perform(post("/admin/unidades/{id}/ativa", sec.getId())
                        .with(user("admin@teste.com").roles("ADMINISTRADOR")).with(csrf()))
                .andExpect(flash().attributeExists("mensagem"));
        em.flush();
        em.clear();
        assertThat(unidadeRepository.findById(sec.getId()).orElseThrow().isAtiva()).isFalse();

        mvc().perform(post("/admin/unidades/{id}/ativa", sec.getId())
                        .with(user("admin@teste.com").roles("ADMINISTRADOR")).with(csrf()))
                .andExpect(flash().attributeExists("mensagem"));
        em.flush();
        em.clear();
        assertThat(unidadeRepository.findById(sec.getId()).orElseThrow().isAtiva()).isTrue();
    }

    @Test
    void soAdministradorAlteraPrazoDaCategoriaEOValorEhValidado() throws Exception {
        Categoria categoria = categoriaRepository.findByAtivaTrueOrderByNome().get(0);
        Long id = categoria.getId();
        int original = categoria.getPrazoReferenciaDias();
        int novo = original == 12 ? 13 : 12;

        mvc().perform(post("/admin/categorias/{id}/prazo", id)
                        .with(user("s@teste.com").roles("SERVIDOR")).with(csrf())
                        .param("prazoDias", String.valueOf(novo)))
                .andExpect(status().isForbidden());

        for (String invalido : new String[]{"0", "366", "-5"}) {
            mvc().perform(post("/admin/categorias/{id}/prazo", id)
                            .with(user("a@teste.com").roles("ADMINISTRADOR")).with(csrf())
                            .param("prazoDias", invalido))
                    .andExpect(flash().attributeExists("erro"));
        }
        em.flush();
        em.clear();
        assertThat(categoriaRepository.findById(id).orElseThrow().getPrazoReferenciaDias()).isEqualTo(original);

        mvc().perform(post("/admin/categorias/{id}/prazo", id)
                        .with(user("a@teste.com").roles("ADMINISTRADOR")).with(csrf())
                        .param("prazoDias", String.valueOf(novo)))
                .andExpect(flash().attributeExists("mensagem"));
        em.flush();
        em.clear();
        assertThat(categoriaRepository.findById(id).orElseThrow().getPrazoReferenciaDias()).isEqualTo(novo);
    }
}