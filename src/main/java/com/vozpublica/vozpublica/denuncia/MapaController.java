package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class MapaController {

    private static final List<String> REGIOES = List.of(
            "RPA 1 - Centro", "RPA 2 - Norte", "RPA 3 - Noroeste",
            "RPA 4 - Oeste", "RPA 5 - Sudoeste", "RPA 6 - Sul");

    private final MapaService mapaService;
    private final CategoriaRepository categoriaRepository;

    public MapaController(MapaService mapaService, CategoriaRepository categoriaRepository) {
        this.mapaService = mapaService;
        this.categoriaRepository = categoriaRepository;
    }

    @GetMapping("/mapa")
    public String mapa(@ModelAttribute("filtro") FiltroDenuncia filtro, BindingResult binding, Model model) {
        model.addAttribute("categorias", categoriaRepository.findByAtivaTrueOrderByNome());
        model.addAttribute("regioes", REGIOES);
        model.addAttribute("prioridades", Prioridade.values());
        model.addAttribute("statuses", StatusDenuncia.values());
        return "mapa";
    }

    /** valores inválidos nos filtros são ignorados, como na listagem */
    @GetMapping("/mapa/dados")
    @ResponseBody
    public MapaService.Resultado dados(@ModelAttribute("filtro") FiltroDenuncia filtro, BindingResult binding) {
        return mapaService.buscar(filtro);
    }
}