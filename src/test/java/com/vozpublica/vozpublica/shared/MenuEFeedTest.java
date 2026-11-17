package com.vozpublica.vozpublica.shared;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaConsultaService;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.denuncia.FiltroDenuncia;
import com.vozpublica.vozpublica.denuncia.Foto;
import com.vozpublica.vozpublica.denuncia.FotoRepository;
import com.vozpublica.vozpublica.denuncia.Prioridade;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class MenuEFeedTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private FotoRepository fotoRepository;
    @Autowired private DenunciaConsultaService consulta;
    @PersistenceContext private EntityManager em;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** perfil null = visitante. O usuário não precisa existir no banco para este tipo de teste. */
    private String pagina(String url, String perfil, String... params) throws Exception {
        var req = get(url);
        if (perfil != null) req.with(user("menu." + perfil + "@teste.com").roles(perfil));
        for (int i = 0; i < params.length; i += 2) req.param(params[i], params[i + 1]);
        return mvc().perform(req).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Denuncia denuncia(String bairro, String descricao) {
        User autor = userRepository.saveAndFlush(new User("Autor", "feed." + UUID.randomUUID() + "@teste.com",
                "hash", Role.CIDADAO));
        return denunciaRepository.saveAndFlush(new Denuncia(autor,
                categoriaRepository.findByAtivaTrueOrderByNome().get(0), "Ocorrência " + UUID.randomUUID(),
                descricao, null, bairro, "RPA 1 - Centro", null, null, Prioridade.MEDIA));
    }

    @Test
    void visitanteVeEntrarECadastrarMasNaoOsLinksDeEquipeNemAdmin() throws Exception {
        String html = pagina("/denuncias", null);

        assertThat(html).contains("Entrar").contains("Cadastrar");
        assertThat(html).doesNotContain(">Sair<").doesNotContain("/gestao/dashboard")
                .doesNotContain("/admin/usuarios");
    }

    @Test
    void cidadaoVeSairMasNaoOsLinksDeEquipeNemAdmin() throws Exception {
        String html = pagina("/denuncias", "CIDADAO");

        assertThat(html).contains(">Sair<");
        assertThat(html).doesNotContain("/gestao/dashboard").doesNotContain("/admin/usuarios")
                .doesNotContain(">Entrar<");
    }

    @Test
    void servidorVeOsLinksDaEquipeMasNaoOsDeAdministracao() throws Exception {
        String html = pagina("/denuncias", "SERVIDOR");

        assertThat(html).contains("/gestao/dashboard").contains("/gestao/relatorios")
                .contains("/gestao/atrasadas");
        assertThat(html).doesNotContain("/admin/usuarios").doesNotContain("/admin/auditoria");
    }

    @Test
    void administradorVeTodosOsLinks() throws Exception {
        String html = pagina("/denuncias", "ADMINISTRADOR");

        assertThat(html).contains("/gestao/dashboard").contains("/admin/moderacao")
                .contains("/admin/usuarios").contains("/admin/auditoria")
                .contains("/admin/unidades").contains("/admin/categorias");
    }

    @Test
    void feedMostraAPrimeiraFotoDeCadaOcorrencia() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        Denuncia d = denuncia(bairro, "Descrição de teste do problema.");
        Foto primeira = fotoRepository.saveAndFlush(new Foto(d, "a.png"));
        Foto segunda = fotoRepository.saveAndFlush(new Foto(d, "b.png"));
        em.flush();
        em.clear();

        FiltroDenuncia filtro = new FiltroDenuncia();
        filtro.setBairro(bairro);
        assertThat(consulta.buscar(filtro, 0).fotos().get(d.getId())).isEqualTo(primeira.getId());

        String html = pagina("/denuncias", null, "bairro", bairro);
        assertThat(html).contains("/fotos/" + primeira.getId()).doesNotContain("/fotos/" + segunda.getId());
    }

    @Test
    void ocorrenciaSemFotoNaoMostraImagem() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        denuncia(bairro, "Descrição de teste do problema.");

        String html = pagina("/denuncias", null, "bairro", bairro);

        assertThat(html).doesNotContain("/fotos/").doesNotContain("card-img-top");
    }

    @Test
    void descricaoLongaApareceResumidaEOCardTemBotaoDeCopiarLink() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        denuncia(bairro, "x".repeat(300) + "FIMDOTEXTO");

        String html = pagina("/denuncias", null, "bairro", bairro);

        assertThat(html).doesNotContain("FIMDOTEXTO");
        assertThat(html).contains("js-copiar").contains("Ver detalhes");
    }

    @Test
    void feedMostraSituacaoComRotuloLegivel() throws Exception {
        String bairro = "B-" + UUID.randomUUID();
        denuncia(bairro, "Descrição de teste do problema.");

        String html = pagina("/denuncias", null, "bairro", bairro);

        assertThat(html).contains("Recebida").contains("text-bg-secondary");
    }

    @Test
    void blocoDeFiltrosSoAbreQuandoHaFiltroAtivo() {
        FiltroDenuncia vazio = new FiltroDenuncia();
        assertThat(vazio.temFiltros()).isFalse();

        FiltroDenuncia comBairro = new FiltroDenuncia();
        comBairro.setBairro("Boa Vista");
        assertThat(comBairro.temFiltros()).isTrue();

        FiltroDenuncia comOrdem = new FiltroDenuncia();
        comOrdem.setOrdem(FiltroDenuncia.Ordem.URGENTES);
        assertThat(comOrdem.temFiltros()).isTrue();

        FiltroDenuncia espacos = new FiltroDenuncia();
        espacos.setBairro("   ");
        assertThat(espacos.temFiltros()).isFalse();
    }
}