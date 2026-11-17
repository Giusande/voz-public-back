package com.vozpublica.vozpublica.relatorio;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.unidade.UnidadeRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

@Controller
@RequestMapping("/gestao")
public class RelatorioController {

    private static final List<String> REGIOES = List.of(
            "RPA 1 - Centro", "RPA 2 - Norte", "RPA 3 - Noroeste",
            "RPA 4 - Oeste", "RPA 5 - Sudoeste", "RPA 6 - Sul");

    private final RelatorioService relatorioService;
    private final CategoriaRepository categoriaRepository;
    private final UnidadeRepository unidadeRepository;

    public RelatorioController(RelatorioService relatorioService,
                               CategoriaRepository categoriaRepository,
                               UnidadeRepository unidadeRepository) {
        this.relatorioService = relatorioService;
        this.categoriaRepository = categoriaRepository;
        this.unidadeRepository = unidadeRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(@ModelAttribute("filtro") FiltroRelatorio filtro,
                            BindingResult binding, // valores inválidos nos filtros são ignorados
                            Model model) {
        model.addAttribute("painel", relatorioService.painel(filtro));
        opcoes(model);
        return "gestao-dashboard";
    }

    @GetMapping("/relatorios")
    public String relatorios(@ModelAttribute("filtro") FiltroRelatorio filtro,
                             BindingResult binding, Model model) {
        model.addAttribute("linhas", relatorioService.relatorioAgrupado(filtro));
        opcoes(model);
        return "gestao-relatorios";
    }

    @GetMapping("/relatorios/agrupado.csv")
    public ResponseEntity<byte[]> csvAgrupado(@ModelAttribute("filtro") FiltroRelatorio filtro,
                                              BindingResult binding, Authentication authentication) {
        return csv("relatorio-" + filtro.getDimensao().name().toLowerCase(),
                () -> relatorioService.exportarAgrupado(filtro, authentication.getName()));
    }

    @GetMapping("/relatorios/ocorrencias.csv")
    public ResponseEntity<byte[]> csvOcorrencias(@ModelAttribute("filtro") FiltroRelatorio filtro,
                                                 BindingResult binding, Authentication authentication) {
        return csv("ocorrencias",
                () -> relatorioService.exportarOcorrencias(filtro, authentication.getName()));
    }

    private ResponseEntity<byte[]> csv(String base, Supplier<byte[]> geracao) {
        try {
            byte[] corpo = geracao.get();
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(base + "-" + LocalDate.now() + ".csv").build().toString())
                    .body(corpo);
        } catch (RelatorioException e) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .header(HttpHeaders.LOCATION, "/gestao/relatorios?erro=limite").build();
        }
    }

    private void opcoes(Model model) {
        model.addAttribute("categorias", categoriaRepository.findByAtivaTrueOrderByNome());
        model.addAttribute("unidades", unidadeRepository.findAllByOrderByNomeAsc());
        model.addAttribute("regioes", REGIOES);
        model.addAttribute("dimensoes", Dimensao.values());
    }
}