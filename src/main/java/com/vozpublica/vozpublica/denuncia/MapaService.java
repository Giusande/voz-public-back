package com.vozpublica.vozpublica.denuncia;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MapaService {

    public static final int MAX_MARCADORES = 1000;

    public record Marcador(Long id, String titulo, String categoria, String situacao,
                           String bairro, double lat, double lng) {}

    public record Resultado(List<Marcador> marcadores, boolean truncado) {}

    private final DenunciaRepository denunciaRepository;

    public MapaService(DenunciaRepository denunciaRepository) {
        this.denunciaRepository = denunciaRepository;
    }

    @Transactional(readOnly = true)
    public Resultado buscar(FiltroDenuncia filtro) {
        Specification<Denuncia> comCoordenadas = (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("latitude")), cb.isNotNull(root.get("longitude")));

        var page = denunciaRepository.findAll(
                DenunciaSpecs.de(filtro).and(comCoordenadas),
                PageRequest.of(0, MAX_MARCADORES + 1, Sort.by(Sort.Direction.DESC, "dataCriacao")));

        List<Denuncia> lista = page.getContent();
        boolean truncado = lista.size() > MAX_MARCADORES;
        List<Marcador> marcadores = lista.stream().limit(MAX_MARCADORES)
                .map(d -> new Marcador(d.getId(), d.getTitulo(), d.getCategoria().getNome(),
                        d.getStatus().getRotulo(), d.getBairro(),
                        coordenada(d.getLatitude()), coordenada(d.getLongitude())))
                .toList();
        return new Resultado(marcadores, truncado);
    }

    private static double coordenada(BigDecimal v) {
        return v.doubleValue();
    }
}