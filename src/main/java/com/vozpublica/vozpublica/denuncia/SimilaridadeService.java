package com.vozpublica.vozpublica.denuncia;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

@Service
public class SimilaridadeService {

    public record Similar(Long id, String titulo, String bairro, StatusDenuncia status, int pontos) {}

    private static final double RAIO_METROS = 300;
    private static final BigDecimal DELTA_GRAUS = new BigDecimal("0.003"); // ~330 m
    private static final int MAX_CANDIDATAS = 50;
    private static final int MAX_RESULTADOS = 5;
    private static final List<StatusDenuncia> ENCERRADAS =
            List.of(StatusDenuncia.RESOLVIDA, StatusDenuncia.INDEFERIDA);

    private final DenunciaRepository denunciaRepository;

    public SimilaridadeService(DenunciaRepository denunciaRepository) {
        this.denunciaRepository = denunciaRepository;
    }

    @Transactional(readOnly = true)
    public List<Similar> buscar(Long categoriaId, String bairro, String titulo,
                                BigDecimal latitude, BigDecimal longitude) {
        if (categoriaId == null) return List.of();

        String bairroNorm = bairro == null ? "" : bairro.trim().toLowerCase(Locale.ROOT);
        boolean temCoordenadas = latitude != null && longitude != null
                && latitude.abs().compareTo(BigDecimal.valueOf(90)) <= 0
                && longitude.abs().compareTo(BigDecimal.valueOf(180)) <= 0;

        if (bairroNorm.isEmpty() && !temCoordenadas) return List.of();

        // Sem coordenadas, usamos uma faixa impossível (1000..1001), que nunca casa com nada
        BigDecimal latMin = temCoordenadas ? latitude.subtract(DELTA_GRAUS) : BigDecimal.valueOf(1000);
        BigDecimal latMax = temCoordenadas ? latitude.add(DELTA_GRAUS) : BigDecimal.valueOf(1001);
        BigDecimal lonMin = temCoordenadas ? longitude.subtract(DELTA_GRAUS) : BigDecimal.valueOf(1000);
        BigDecimal lonMax = temCoordenadas ? longitude.add(DELTA_GRAUS) : BigDecimal.valueOf(1001);

        List<Denuncia> candidatas = denunciaRepository.buscarCandidatas(
                categoriaId, ENCERRADAS, bairroNorm, latMin, latMax, lonMin, lonMax,
                PageRequest.of(0, MAX_CANDIDATAS));

        Set<String> palavrasDigitadas = palavras(titulo);
        List<Similar> resultado = new ArrayList<>();

        for (Denuncia d : candidatas) {
            int pontos = 0;
            if (!bairroNorm.isEmpty() && d.getBairro().toLowerCase(Locale.ROOT).equals(bairroNorm)) {
                pontos += 1;
            }
            if (temCoordenadas && d.getLatitude() != null && d.getLongitude() != null
                    && distanciaMetros(latitude.doubleValue(), longitude.doubleValue(),
                            d.getLatitude().doubleValue(), d.getLongitude().doubleValue()) <= RAIO_METROS) {
                pontos += 3;
            }
            Set<String> emComum = palavras(d.getTitulo());
            emComum.retainAll(palavrasDigitadas);
            pontos += 2 * emComum.size();

            if (pontos > 0) {
                resultado.add(new Similar(d.getId(), d.getTitulo(), d.getBairro(), d.getStatus(), pontos));
            }
        }

        resultado.sort(Comparator.comparingInt(Similar::pontos).reversed()
                .thenComparing(Similar::id, Comparator.reverseOrder()));
        return resultado.size() > MAX_RESULTADOS ? resultado.subList(0, MAX_RESULTADOS) : resultado;
    }

    /** Palavras com 4+ letras, sem acentos e em minúsculas. */
    private static Set<String> palavras(String texto) {
        Set<String> conjunto = new HashSet<>();
        if (texto == null) return conjunto;
        String limpo = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        for (String p : limpo.split("[^a-z0-9]+")) {
            if (p.length() >= 4) conjunto.add(p);
        }
        return conjunto;
    }

    /** Distância entre dois pontos (fórmula de Haversine). */
    private static double distanciaMetros(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}