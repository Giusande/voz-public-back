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

    public record Resultado(Page<Denuncia> page, Map<Long, Contadores> contadores) {}

    private final DenunciaRepository denunciaRepository;
    private final CurtidaRepository curtidaRepository;
    private final IncentivoRepository incentivoRepository;
    private final ComentarioRepository comentarioRepository;

    public DenunciaConsultaService(DenunciaRepository denunciaRepository,
                                   CurtidaRepository curtidaRepository,
                                   IncentivoRepository incentivoRepository,
                                   ComentarioRepository comentarioRepository) {
        this.denunciaRepository = denunciaRepository;
        this.curtidaRepository = curtidaRepository;
        this.incentivoRepository = incentivoRepository;
        this.comentarioRepository = comentarioRepository;
    }

    @Transactional(readOnly = true)
    public Resultado buscar(FiltroDenuncia filtro, int pagina) {
        Page<Denuncia> page = denunciaRepository.findAll(
                DenunciaSpecs.de(filtro), PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA));

        List<Long> ids = page.getContent().stream().map(Denuncia::getId).toList();
        Map<Long, Contadores> contadores = new HashMap<>();

        if (!ids.isEmpty()) {
            Map<Long, Long> curtidas = mapa(curtidaRepository.contarPorDenuncia(ids));
            Map<Long, Long> incentivos = mapa(incentivoRepository.contarPorDenuncia(ids));
            Map<Long, Long> comentarios = mapa(comentarioRepository.contarPorDenuncia(ids));
            for (Long id : ids) {
                contadores.put(id, new Contadores(
                        curtidas.getOrDefault(id, 0L),
                        incentivos.getOrDefault(id, 0L),
                        comentarios.getOrDefault(id, 0L)));
            }
        }
        return new Resultado(page, contadores);
    }

    private static Map<Long, Long> mapa(List<ContagemPorDenuncia> lista) {
        return lista.stream().collect(
                Collectors.toMap(ContagemPorDenuncia::denunciaId, ContagemPorDenuncia::total));
    }
}