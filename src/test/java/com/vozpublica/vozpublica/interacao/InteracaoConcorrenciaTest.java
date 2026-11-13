package com.vozpublica.vozpublica.interacao;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.denuncia.Prioridade;
import com.vozpublica.vozpublica.user.Role;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class InteracaoConcorrenciaTest {

    @Autowired private InteracaoService service;
    @Autowired private UserRepository userRepository;
    @Autowired private DenunciaRepository denunciaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private IncentivoRepository incentivoRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void incentivosSimultaneosDoMesmoUsuarioGravamApenasUm() throws Exception {
        String email = "conc." + UUID.randomUUID() + "@teste.com";
        User autor = userRepository.saveAndFlush(new User("Concorrência", email, "hash", Role.CIDADAO));
        var categoria = categoriaRepository.findByAtivaTrueOrderByNome().get(0);
        Denuncia d = denunciaRepository.saveAndFlush(new Denuncia(autor, categoria,
                "Teste de concorrência", "Descrição de teste do problema.",
                null, "Boa Vista", "RPA 1 - Centro", null, null, Prioridade.MEDIA));

        int tentativas = 10;
        ExecutorService pool = Executors.newFixedThreadPool(tentativas);
        try {
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Boolean>> futuros = new ArrayList<>();
            for (int i = 0; i < tentativas; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return service.incentivar(d.getId(), email);
                }));
            }
            largada.countDown();

            int gravados = 0;
            for (Future<Boolean> f : futuros) {
                if (f.get(30, TimeUnit.SECONDS)) gravados++;
            }

            assertThat(gravados).isEqualTo(1);
            assertThat(incentivoRepository.countByDenunciaId(d.getId())).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM incentivos WHERE denuncia_id = ?", d.getId());
            denunciaRepository.deleteById(d.getId());
            userRepository.deleteById(autor.getId());
        }
    }
}