package com.vozpublica.vozpublica.encaminhamento;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.denuncia.HistoricoStatusRepository;
import com.vozpublica.vozpublica.denuncia.Prioridade;
import com.vozpublica.vozpublica.denuncia.StatusDenuncia;
import com.vozpublica.vozpublica.relatorio.FiltroRelatorio;
import com.vozpublica.vozpublica.relatorio.RelatorioService;
import com.vozpublica.vozpublica.unidade.TipoUnidade;
import com.vozpublica.vozpublica.unidade.Unidade;
import com.vozpublica.vozpublica.unidade.UnidadeRepository;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PrazoReaberturaTest {

    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private EncaminhamentoRepository encaminhamentoRepository;
    @Autowired private HistoricoStatusRepository historicoRepository;
    @Autowired private AtrasoRepository atrasoRepository;
    @Autowired private RelatorioService relatorioService;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    private User usuario(Role papel) {
        return userRepository.saveAndFlush(new User("Teste", "reab." + UUID.randomUUID() + "@teste.com",
                "hash", papel));
    }

    /** Criada em 2020 (muito antiga) e com a situação informada. */
    private Denuncia antiga(User autor, String bairro, String status) {
        Denuncia d = denunciaRepository.saveAndFlush(new Denuncia(autor,
                categoriaRepository.findByAtivaTrueOrderByNome().get(0), "Ocorrência " + UUID.randomUUID(),
                "Descrição de teste do problema.", null, bairro, "RPA 1 - Centro",
                null, null, Prioridade.MEDIA));
        jdbc.update("UPDATE denuncias SET data_criacao = ?, status = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2020, 1, 1, 12, 0)), status, d.getId());
        return d;
    }

    private void reabrir(Denuncia d, User responsavel, LocalDateTime quando) {
        jdbc.update("INSERT INTO historico_status (denuncia_id, status_anterior, status_novo, "
                        + "responsavel_id, observacao, data_alteracao) VALUES (?, 'RESOLVIDA', 'REABERTA', ?, 'Voltou', ?)",
                d.getId(), responsavel.getId(), Timestamp.valueOf(quando));
    }

    private void encaminhar(Denuncia d, User responsavel, LocalDate prazo, LocalDateTime quando) {
        Unidade unidade = unidadeRepository.saveAndFlush(
                new Unidade("Sec-" + UUID.randomUUID(), TipoUnidade.SECRETARIA, null));
        jdbc.update("INSERT INTO encaminhamentos (denuncia_id, unidade_id, prazo_especifico, "
                        + "responsavel_id, data_encaminhamento) VALUES (?, ?, ?, ?, ?)",
                d.getId(), unidade.getId(), prazo == null ? null : java.sql.Date.valueOf(prazo),
                responsavel.getId(), Timestamp.valueOf(quando));
    }

    private void limpar() {
        em.flush();
        em.clear();
    }

    private List<Long> idsAtrasadasNoSql() {
        return atrasoRepository.buscarAtrasadas(LocalDate.now(), PageRequest.of(0, 100_000))
                .getContent().stream().map(DenunciaAtrasada::getId).toList();
    }

    private boolean atrasadaEmJava(Denuncia d) {
        Denuncia relida = denunciaRepository.findComDetalhesById(d.getId()).orElseThrow();
        Encaminhamento atual = encaminhamentoRepository.findFirstByDenunciaIdOrderByIdDesc(d.getId()).orElse(null);
        LocalDateTime reabertura = historicoRepository.ultimaDataPara(d.getId(), StatusDenuncia.REABERTA);
        return Prazo.calcular(relida, atual, reabertura, LocalDate.now()).atrasada();
    }

    private void confere(Denuncia d, boolean esperadoAtrasada, List<Long> sql) {
        assertThat(sql.contains(d.getId())).as("SQL").isEqualTo(esperadoAtrasada);
        assertThat(atrasadaEmJava(d)).as("Java").isEqualTo(esperadoAtrasada);
    }

    @Test
    void semReaberturaACriacaoAntigaContinuaAtrasada() {
        Denuncia d = antiga(usuario(Role.CIDADAO), "B-" + UUID.randomUUID(), "EM_ANALISE");
        limpar();

        confere(d, true, idsAtrasadasNoSql());
    }

    @Test
    void reaberturaRecenteReiniciaOPrazo() {
        User u = usuario(Role.SERVIDOR);
        Denuncia d = antiga(u, "B-" + UUID.randomUUID(), "REABERTA");
        reabrir(d, u, LocalDateTime.now().minusDays(1));
        limpar();

        confere(d, false, idsAtrasadasNoSql());
    }

    @Test
    void reaberturaAntigaContinuaPodendoFicarAtrasada() {
        User u = usuario(Role.SERVIDOR);
        Denuncia d = antiga(u, "B-" + UUID.randomUUID(), "REABERTA");
        reabrir(d, u, LocalDateTime.of(2020, 6, 1, 12, 0));
        limpar();

        confere(d, true, idsAtrasadasNoSql());
    }

    @Test
    void usaAUltimaReaberturaQuandoHaVarias() {
        User u = usuario(Role.SERVIDOR);
        Denuncia d = antiga(u, "B-" + UUID.randomUUID(), "REABERTA");
        reabrir(d, u, LocalDateTime.of(2020, 6, 1, 12, 0));
        reabrir(d, u, LocalDateTime.now().minusDays(1));
        limpar();

        confere(d, false, idsAtrasadasNoSql());
    }

    @Test
    void prazoEspecificoAnteriorAReaberturaEhIgnorado() {
        User u = usuario(Role.SERVIDOR);
        Denuncia d = antiga(u, "B-" + UUID.randomUUID(), "REABERTA");
        // prazo antigo (já vencido), definido ANTES de reabrir: se valesse, estaria atrasada
        encaminhar(d, u, LocalDate.of(2020, 2, 1), LocalDateTime.now().minusDays(10));
        reabrir(d, u, LocalDateTime.now().minusDays(1));
        limpar();

        confere(d, false, idsAtrasadasNoSql());
    }

    @Test
    void prazoEspecificoPosteriorAReaberturaValeEPrevaleceSobreAData() {
        User u = usuario(Role.SERVIDOR);
        Denuncia d = antiga(u, "B-" + UUID.randomUUID(), "ENCAMINHADA");
        reabrir(d, u, LocalDateTime.of(2020, 6, 1, 12, 0));
        // sem o prazo específico, a reabertura de 2020 deixaria a ocorrência atrasada
        encaminhar(d, u, LocalDate.now().plusDays(5), LocalDateTime.of(2020, 6, 2, 12, 0));
        limpar();

        confere(d, false, idsAtrasadasNoSql());
    }

    @Test
    @WithMockUser(roles = "SERVIDOR")
    void dashboardUsaAMesmaRegra() {
        String bairro = "B-" + UUID.randomUUID();
        User u = usuario(Role.SERVIDOR);
        Denuncia recente = antiga(u, bairro, "REABERTA");
        Denuncia antigaDeFato = antiga(u, bairro, "REABERTA");
        reabrir(recente, u, LocalDateTime.now().minusDays(1));
        reabrir(antigaDeFato, u, LocalDateTime.of(2020, 6, 1, 12, 0));
        limpar();

        FiltroRelatorio filtro = new FiltroRelatorio();
        filtro.setBairro(bairro);
        var geral = relatorioService.painel(filtro).geral();

        assertThat(geral.total()).isEqualTo(2);
        assertThat(geral.atrasadas()).isEqualTo(1);
    }
}