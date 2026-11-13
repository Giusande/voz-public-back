package com.vozpublica.vozpublica.denuncia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ArmazenamentoFotoService {

    public static final int MAX_FOTOS = 5;
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Pattern NOME_VALIDO = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)");

    public record FotoProcessada(byte[] bytes, String extensao) {}

    private final Path raiz;

    public ArmazenamentoFotoService(@Value("${app.upload.dir:uploads}") String diretorio) {
        this.raiz = Path.of(diretorio).toAbsolutePath().normalize();
    }

    /** Valida e reprocessa as imagens SEM gravar nada em disco. */
    public List<FotoProcessada> validar(List<MultipartFile> arquivos) {
        List<MultipartFile> enviados = arquivos == null ? List.of()
                : arquivos.stream().filter(a -> a != null && !a.isEmpty()).toList();

        if (enviados.size() > MAX_FOTOS) {
            throw new FotoInvalidaException("Envie no máximo " + MAX_FOTOS + " fotos.");
        }
        List<FotoProcessada> resultado = new ArrayList<>();
        for (MultipartFile arquivo : enviados) {
            resultado.add(processar(arquivo));
        }
        return resultado;
    }

    private FotoProcessada processar(MultipartFile arquivo) {
        if (arquivo.getSize() > MAX_BYTES) {
            throw new FotoInvalidaException("Cada foto deve ter no máximo 5 MB.");
        }
        byte[] bytes;
        try {
            bytes = arquivo.getBytes();
        } catch (IOException e) {
            throw new FotoInvalidaException("Não foi possível ler o arquivo enviado.");
        }
        String formato = detectarFormato(bytes);
        if (formato == null) {
            throw new FotoInvalidaException("Formato não permitido. Envie imagens JPEG ou PNG.");
        }

        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new FotoInvalidaException("O arquivo não é uma imagem válida.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new FotoInvalidaException("A imagem tem dimensões grandes demais.");
                }
                BufferedImage imagem = reader.read(0);
                ByteArrayOutputStream saida = new ByteArrayOutputStream();
                // Regravar a imagem descarta metadados (EXIF/GPS)
                if (imagem == null || !ImageIO.write(imagem, formato, saida)) {
                    throw new FotoInvalidaException("O arquivo não é uma imagem válida.");
                }
                return new FotoProcessada(saida.toByteArray(), formato);
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new FotoInvalidaException("O arquivo não é uma imagem válida.");
        }
    }

    /** Confere a assinatura real do arquivo, ignorando nome e Content-Type do cliente. */
    private String detectarFormato(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (b.length >= 8) {
            for (int i = 0; i < 8; i++) {
                if (b[i] != png[i]) return null;
            }
            return "png";
        }
        return null;
    }

    public String salvar(FotoProcessada foto) {
        try {
            Files.createDirectories(raiz);
            String nome = UUID.randomUUID() + "." + foto.extensao();
            Files.write(raiz.resolve(nome), foto.bytes(), StandardOpenOption.CREATE_NEW);
            return nome;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path caminho(String nome) {
        if (nome == null || !NOME_VALIDO.matcher(nome).matches()) {
            throw new IllegalArgumentException("Nome de arquivo inválido.");
        }
        return raiz.resolve(nome);
    }

    public void apagar(String nome) {
        try {
            Files.deleteIfExists(caminho(nome));
        } catch (IOException | IllegalArgumentException ignorado) {
            // limpeza "melhor esforço"
        }
    }
}