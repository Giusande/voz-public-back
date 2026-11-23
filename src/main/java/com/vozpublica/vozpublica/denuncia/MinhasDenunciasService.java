package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.denuncia.DenunciaConsultaService.Contadores;
import com.vozpublica.vozpublica.encaminhamento.Encaminhamento;
import com.vozpublica.vozpublica.encaminhamento.EncaminhamentoRepository;
import com.vozpublica.vozpublica.solicitacao.SolicitacaoInfo;
import com.vozpublica.vozpublica.solicitacao.SolicitacaoInfoRepository;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MinhasDenunciasService {

    private static final int TAMANHO_PAGINA = 10;

    public record Visao(Page<Denuncia> page, Map<Long, Contadores> contadores,
                        Map<Long, Denuncia> principais, Map<Long, Encaminhamento> encaminhamentos,
                        Map<Long, List<SolicitacaoInfo>> solicitacoes) {}

    private final UserRepository userRepository;
    private final DenunciaRepository denunciaRepository;
    private final DenunciaPerfilRepository perfilRepository;
    private final DenunciaConsultaService consultaService;
    private final EncaminhamentoRepository encaminhamentoRepository;
    private final SolicitacaoInfoRepository solicitacaoRepository;

    public MinhasDenunciasService(UserRepository userRepository, DenunciaRepository denunciaRepository,
                                  DenunciaPerfilRepository perfilRepository,
                                  DenunciaConsultaService consultaService,
                                  EncaminhamentoRepository encaminhamentoRepository,
                                  SolicitacaoInfoRepository solicitacaoRepository) {
        this.userRepository = userRepository;
        this.denunciaRepository = denunciaRepository;
        this.perfilRepository = perfilRepository;
        this.consultaService = consultaService;
        this.encaminhamentoRepository = encaminhamentoRepository;
        this.solicitacaoRepository = solicitacaoRepository;
    }

    @Transactional(readOnly = true)
    public Visao listar(String email, int pagina) {
        Long autorId = userRepository.findByEmail(email.trim().toLowerCase())
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));

        Page<Denuncia> page = perfilRepository.findByAutorIdOrderByDataCriacaoDesc(
                autorId, PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA));
        var complemento = consultaService.complementar(page);

        List<Long> ids = page.getContent().stream().map(Denuncia::getId).toList();
        List<Long> idsPrincipais = page.getContent().stream()
                .map(Denuncia::getDuplicadaDeId).filter(x -> x != null).distinct().toList();

        Map<Long, Denuncia> principais = idsPrincipais.isEmpty() ? new HashMap<>()
                : denunciaRepository.findAllById(idsPrincipais).stream()
                        .collect(Collectors.toMap(Denuncia::getId, Function.identity()));
        Map<Long, Encaminhamento> encaminhamentos = ids.isEmpty() ? new HashMap<>()
                : encaminhamentoRepository.atuaisDe(ids).stream()
                        .collect(Collectors.toMap(Encaminhamento::getDenunciaId, Function.identity()));
        Map<Long, List<SolicitacaoInfo>> solicitacoes = ids.isEmpty() ? new HashMap<>()
                : solicitacaoRepository.findByDenunciaIdInOrderByIdDesc(ids).stream()
                        .collect(Collectors.groupingBy(SolicitacaoInfo::getDenunciaId));

        return new Visao(page, complemento.contadores(), principais, encaminhamentos, solicitacoes);
    }
}