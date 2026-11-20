package com.vozpublica.vozpublica.shared;

import com.vozpublica.vozpublica.denuncia.Denuncia;
import com.vozpublica.vozpublica.denuncia.DenunciaConsultaService;
import com.vozpublica.vozpublica.denuncia.FiltroDenuncia;
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

    private final DenunciaConsultaService consulta;
    private final TransparenciaService transparencia;
    private final InteracaoService interacaoService;

    public HomeController(DenunciaConsultaService consulta, TransparenciaService transparencia,
                          InteracaoService interacaoService) {
        this.consulta = consulta;
        this.transparencia = transparencia;
        this.interacaoService = interacaoService;
    }

    @GetMapping("/")
    public String home(@ModelAttribute("filtro") FiltroDenuncia filtro,
                       BindingResult binding, // valores inválidos nos filtros são ignorados
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

        // Mais incentivadas: só as que têm ao menos um incentivo
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