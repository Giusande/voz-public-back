package com.vozpublica.vozpublica.encaminhamento;

import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.HistoricoStatusRepository;
import com.vozpublica.vozpublica.denuncia.StatusDenuncia;
import com.vozpublica.vozpublica.unidade.Unidade;
import com.vozpublica.vozpublica.unidade.UnidadeRepository;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class EncaminhamentoDetalheService {

    /** historico: do mais recente ao mais antigo; atual é o primeiro (ou null). */
    public record Visao(List<Encaminhamento> historico, Encaminhamento atual, Prazo prazo) {}

    public record Opcoes(List<Unidade> unidades, List<User> servidores) {}

    private final EncaminhamentoRepository encaminhamentoRepository;
    private final HistoricoStatusRepository historicoRepository;
    private final UnidadeRepository unidadeRepository;
    private final UserRepository userRepository;

    public EncaminhamentoDetalheService(EncaminhamentoRepository encaminhamentoRepository,
                                        HistoricoStatusRepository historicoRepository,
                                        UnidadeRepository unidadeRepository,
                                        UserRepository userRepository) {
        this.encaminhamentoRepository = encaminhamentoRepository;
        this.historicoRepository = historicoRepository;
        this.unidadeRepository = unidadeRepository;
        this.userRepository = userRepository;
    }

    /** A denúncia deve ter vindo com a categoria carregada (findComDetalhesById faz isso). */
    @Transactional(readOnly = true)
    public Visao carregar(Denuncia denuncia) {
        List<Encaminhamento> historico =
                encaminhamentoRepository.findByDenunciaIdOrderByIdDesc(denuncia.getId());
        Encaminhamento atual = historico.isEmpty() ? null : historico.get(0);
        var ultimaReabertura = historicoRepository.ultimaDataPara(denuncia.getId(), StatusDenuncia.REABERTA);
        return new Visao(historico, atual,
                Prazo.calcular(denuncia, atual, ultimaReabertura, LocalDate.now()));
    }

    @PreAuthorize("hasAnyRole('SERVIDOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public Opcoes opcoes() {
        return new Opcoes(
                unidadeRepository.findByAtivaTrueOrderByNome(),
                userRepository.findByRoleInAndEnabledTrueOrderByName(
                        List.of(Role.SERVIDOR, Role.ADMINISTRADOR)));
    }
}