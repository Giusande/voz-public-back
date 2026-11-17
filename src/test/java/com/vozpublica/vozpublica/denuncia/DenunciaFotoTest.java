package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.upload.dir=target/test-uploads")
@Transactional
class DenunciaFotoTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private FotoRepository fotoRepository;
    @Autowired private CategoriaRepository categoriaRepository;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private MockMultipartHttpServletRequestBuilder requisicao(String email, String titulo) {
        Long categoria = categoriaRepository.findByAtivaTrueOrderByNome().get(0).getId();
        MockMultipartHttpServletRequestBuilder b = multipart("/denuncias/nova");
        b.with(user(email).roles("CIDADAO")).with(csrf())
                .param("titulo", titulo)
                .param("descricao", "Descrição suficientemente longa do problema.")
                .param("categoriaId", String.valueOf(categoria))
                .param("bairro", "Boa Vista")
                .param("regiao", "RPA 1 - Centro")
                .param("prioridadeSugerida", "MEDIA");
        return b;
    }

    @Test
    void registraOcorrenciaComFotoEServeAImagemParaVisitante() throws Exception {
        String email = "foto.ok@teste.com";
        userRepository.saveAndFlush(new User("Autor Foto", email, "hash", Role.CIDADAO));

        mvc().perform(requisicao(email, "Com foto")
                        .file(new MockMultipartFile("fotos", "a.png", "image/png", png())))
                .andExpect(status().is3xxRedirection());

        Denuncia d = denunciaRepository.findAll().stream()
                .filter(x -> x.getTitulo().equals("Com foto")).findFirst().orElseThrow();
        List<Foto> fotos = fotoRepository.findByDenunciaIdOrderByIdAsc(d.getId());
        assertThat(fotos).hasSize(1);

        // visitante (sem login) consegue ver a imagem
        mvc().perform(get("/fotos/" + fotos.get(0).getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void arquivoFalsoEhRejeitadoENenhumaOcorrenciaEhCriada() throws Exception {
        String email = "foto.falsa@teste.com";
        userRepository.saveAndFlush(new User("Autor Falso", email, "hash", Role.CIDADAO));
        long antes = denunciaRepository.count();

        mvc().perform(requisicao(email, "Foto falsa")
                        .file(new MockMultipartFile("fotos", "x.jpg", "image/jpeg", "nao sou imagem".getBytes())))
                .andExpect(status().isOk())
                .andExpect(view().name("denuncia-nova"))
                .andExpect(model().attributeExists("erroFotos"));

        assertThat(denunciaRepository.count()).isEqualTo(antes);
    }

    @Test
    void fotoInexistenteDa404() throws Exception {
        mvc().perform(get("/fotos/999999")).andExpect(status().isNotFound());
    }
}