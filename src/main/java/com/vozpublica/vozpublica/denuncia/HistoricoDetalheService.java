package com.vozpublica.vozpublica.denuncia;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HistoricoDetalheService {

    public record Detalhes(List<HistoricoStatus> status,
                           Map<Long, List<Evidencia>> evidencias,
                           List<HistoricoPrioridade> prioridades) {}

    private final HistoricoStatusRepository historicoRepository;
    private final HistoricoPrioridadeRepository prioridadeRepository;
    private final EvidenciaRepository evidenciaRepository;

    public HistoricoDetalheService(HistoricoStatusRepository historicoRepository,
                                   HistoricoPrioridadeRepository prioridadeRepository,
                                   EvidenciaRepository evidenciaRepository) {
        this.historicoRepository = historicoRepository;
        this.prioridadeRepository = prioridadeRepository;
        this.evidenciaRepository = evidenciaRepository;
    }

    @Transactional(readOnly = true)
    public Detalhes carregar(Long denunciaId) {
        List<HistoricoStatus> status = historicoRepository.findByDenunciaIdOrderByDataAlteracaoAsc(denunciaId);

        // todo registro de histórico tem uma entrada no mapa, mesmo sem evidências
        Map<Long, List<Evidencia>> evidencias = new HashMap<>();
        for (HistoricoStatus h : status) {
            evidencias.put(h.getId(), new ArrayList<>());
        }
        if (!status.isEmpty()) {
            for (Evidencia e : evidenciaRepository.findByHistoricoIdInOrderByIdAsc(
                    List.copyOf(evidencias.keySet()))) {
                evidencias.get(e.getHistoricoId()).add(e);
            }
        }
        return new Detalhes(status, evidencias,
                prioridadeRepository.findByDenunciaIdOrderByDataAlteracaoAsc(denunciaId));
    }
}