package com.vozpublica.vozpublica.denuncia;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Controller
public class FotoController {

    private final FotoRepository fotoRepository;
    private final ArmazenamentoFotoService armazenamento;

    public FotoController(FotoRepository fotoRepository, ArmazenamentoFotoService armazenamento) {
        this.fotoRepository = fotoRepository;
        this.armazenamento = armazenamento;
    }

    @GetMapping("/fotos/{id:\\d+}")
    public ResponseEntity<Resource> ver(@PathVariable Long id) {
        Foto foto = fotoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Path arquivo;
        try {
            arquivo = armazenamento.caminho(foto.getCaminhoArquivo());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (!Files.isRegularFile(arquivo)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        MediaType tipo = foto.getCaminhoArquivo().endsWith(".png")
                ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;

        return ResponseEntity.ok()
                .contentType(tipo)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(new FileSystemResource(arquivo));
    }
}