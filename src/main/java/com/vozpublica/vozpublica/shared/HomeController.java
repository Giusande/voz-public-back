package com.vozpublica.vozpublica.shared;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaConsultaService;
import com.vozpublica.vozpublica.denuncia.FiltroDenuncia;
import com.vozpublica.vozpublica.denuncia.Prioridade;
import com.vozpublica.vozpublica.denuncia.StatusDenuncia;
import com.vozpublica.vozpublica.interacao.InteracaoService;
import com.vozpublica.vozpublica.relatorio.TransparenciaService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class HomeController {

    private static final List<String> REGIOES = List.of(
            "RPA 1 - Centro", "RPA 2 - Norte", "RPA 3 - Noroeste",
            "RPA 4 - Oeste", "RPA 5 - Sudoeste", "RPA 6 - Sul");

    private final DenunciaConsultaService consulta;
    private final TransparenciaService transparencia;
    private final InteracaoService interacaoService;
    private final CategoriaRepository categoriaRepository;

    public HomeController(DenunciaConsultaService consulta, TransparenciaService transparencia,
                          InteracaoService interacaoService, CategoriaRepository categoriaRepository) {
        this.consulta = consulta;
        this.transparencia = transparencia;
        this.interacaoService = interacaoService;
        this.categoriaRepository = categoriaRepository;
    }

    @GetMapping({"/", "/denuncias"})
    public String home(@ModelAttribute("filtro") FiltroDenuncia filtro,
                       BindingResult binding, 
                       @RequestParam(defaultValue = "0") int pagina,
                       Authentication authentication,
                       Model model) {
        var feed = consulta.buscar(filtro, pagina);
        model.addAttribute("page", feed.page());
        model.addAttribute("contadores", feed.contadores());
        model.addAttribute("fotos", feed.fotos());

        var marcas = interacaoService.marcas(
                feed.page().getContent().stream().map(Denuncia::getId).toList(),
                authentication != null ? authentication.getName() : null);
        model.addAttribute("curtidos", marcas.curtidas());
        model.addAttribute("incentivados", marcas.incentivos());

        model.addAttribute("categorias", categoriaRepository.findByAtivaTrueOrderByNome());
        model.addAttribute("regioes", REGIOES);
        model.addAttribute("prioridades", Prioridade.values());
        model.addAttribute("statuses", StatusDenuncia.values());
        model.addAttribute("ordens", FiltroDenuncia.Ordem.values());

        FiltroDenuncia porIncentivo = new FiltroDenuncia();
        porIncentivo.setOrdem(FiltroDenuncia.Ordem.INCENTIVADAS);
        var top = consulta.buscar(porIncentivo, 0);
        List<Denuncia> maisIncentivadas = top.page().getContent().stream()
                .filter(d -> top.contadores().get(d.getId()).incentivos() > 0)
                .limit(5).toList();
        model.addAttribute("maisIncentivadas", maisIncentivadas);
        model.addAttribute("contadoresTop", top.contadores());

        model.addAttribute("painel", transparencia.painel(null, null));
        return "index";
    }
}