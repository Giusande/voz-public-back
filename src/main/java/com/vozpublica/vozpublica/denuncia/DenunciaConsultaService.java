package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.comentario.ComentarioRepository;
import com.vozpublica.vozpublica.interacao.CurtidaRepository;
import com.vozpublica.vozpublica.interacao.IncentivoRepository;
import com.vozpublica.vozpublica.shared.ContagemPorDenuncia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DenunciaConsultaService {

    public static final int TAMANHO_PAGINA = 10;

    public record Contadores(long curtidas, long incentivos, long comentarios) {}

    /** fotos: id da ocorrência -> id da primeira foto (ausente se não tiver foto). */
    public record Resultado(Page<Denuncia> page, Map<Long, Contadores> contadores, Map<Long, Long> fotos) {}

    private final DenunciaRepository denunciaRepository;
    private final CurtidaRepository curtidaRepository;
    private final IncentivoRepository incentivoRepository;
    private final ComentarioRepository comentarioRepository;
    private final FotoRepository fotoRepository;

    public DenunciaConsultaService(DenunciaRepository denunciaRepository,
                                   CurtidaRepository curtidaRepository,
                                   IncentivoRepository incentivoRepository,
                                   ComentarioRepository comentarioRepository,
                                   FotoRepository fotoRepository) {
        this.denunciaRepository = denunciaRepository;
        this.curtidaRepository = curtidaRepository;
        this.incentivoRepository = incentivoRepository;
        this.comentarioRepository = comentarioRepository;
        this.fotoRepository = fotoRepository;
    }

    @Transactional(readOnly = true)
    public Resultado buscar(FiltroDenuncia filtro, int pagina) {
        Page<Denuncia> page = denunciaRepository.findAll(
                DenunciaSpecs.de(filtro), PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA));
        return complementar(page);
    }

    /** Contadores e primeira foto de cada ocorrência da página, sem N+1. */
    @Transactional(readOnly = true)
    public Resultado complementar(Page<Denuncia> page) {
        List<Long> ids = page.getContent().stream().map(Denuncia::getId).toList();
        Map<Long, Contadores> contadores = new HashMap<>();
        Map<Long, Long> fotos = new HashMap<>();

        if (!ids.isEmpty()) {
            Map<Long, Long> curtidas = mapa(curtidaRepository.contarPorDenuncia(ids));
            Map<Long, Long> incentivos = mapa(incentivoRepository.contarConsolidadoPorDenuncia(ids));
            Map<Long, Long> comentarios = mapa(comentarioRepository.contarPorDenuncia(ids));
            for (Long id : ids) {
                contadores.put(id, new Contadores(
                        curtidas.getOrDefault(id, 0L),
                        incentivos.getOrDefault(id, 0L),
                        comentarios.getOrDefault(id, 0L)));
            }
            fotos.putAll(mapa(fotoRepository.primeirasFotos(ids)));
        }
        return new Resultado(page, contadores, fotos);
    }

    private static Map<Long, Long> mapa(List<ContagemPorDenuncia> lista) {
        return lista.stream().collect(
                Collectors.toMap(ContagemPorDenuncia::denunciaId, ContagemPorDenuncia::total));
    }
}