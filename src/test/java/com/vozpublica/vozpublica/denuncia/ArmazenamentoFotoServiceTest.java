package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.denuncia.ArmazenamentoFotoService.FotoProcessada;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArmazenamentoFotoServiceTest {

    @TempDir
    Path pasta;

    private byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private MockMultipartFile arquivo(String nome, byte[] conteudo) {
        return new MockMultipartFile("fotos", nome, "image/jpeg", conteudo);
    }

    @Test
    void aceitaPngRealESalvaComNomeGeradoPeloServidor() throws Exception {
        var servico = new ArmazenamentoFotoService(pasta.toString());

        List<FotoProcessada> ok = servico.validar(List.of(arquivo("../../minha foto.png", png())));
        assertThat(ok).hasSize(1);

        String nome = servico.salvar(ok.get(0));
        assertThat(nome).matches("[0-9a-f-]{36}\\.png");
        assertThat(Files.exists(servico.caminho(nome))).isTrue();
    }

    @Test
    void rejeitaTextoDisfarcadoDeImagem() {
        var servico = new ArmazenamentoFotoService(pasta.toString());
        var falso = arquivo("foto.jpg", "<?php echo 'oi'; ?>".getBytes());

        assertThatThrownBy(() -> servico.validar(List.of(falso)))
                .isInstanceOf(FotoInvalidaException.class);
    }

    @Test
    void rejeitaAssinaturaDeJpegComConteudoInvalido() {
        var servico = new ArmazenamentoFotoService(pasta.toString());
        byte[] lixo = new byte[200];
        lixo[0] = (byte) 0xFF; lixo[1] = (byte) 0xD8; lixo[2] = (byte) 0xFF;

        assertThatThrownBy(() -> servico.validar(List.of(arquivo("foto.jpg", lixo))))
                .isInstanceOf(FotoInvalidaException.class);
    }

    @Test
    void rejeitaMaisDeCincoFotos() throws Exception {
        var servico = new ArmazenamentoFotoService(pasta.toString());
        byte[] png = png();
        List<org.springframework.web.multipart.MultipartFile> seis =
                Collections.nCopies(6, arquivo("a.png", png));

        assertThatThrownBy(() -> servico.validar(seis))
                .isInstanceOf(FotoInvalidaException.class);
    }

    @Test
    void rejeitaArquivoMaiorQueOLimite() {
        var servico = new ArmazenamentoFotoService(pasta.toString());
        byte[] grande = new byte[(int) ArmazenamentoFotoService.MAX_BYTES + 1];

        assertThatThrownBy(() -> servico.validar(List.of(arquivo("grande.png", grande))))
                .isInstanceOf(FotoInvalidaException.class);
    }

    @Test
    void caminhoRejeitaNomeComTentativaDePathTraversal() {
        var servico = new ArmazenamentoFotoService(pasta.toString());

        assertThatThrownBy(() -> servico.caminho("../segredo.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}