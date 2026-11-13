package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.comentario.ComentarioForm;
import com.vozpublica.vozpublica.comentario.ComentarioRepository;
import com.vozpublica.vozpublica.encaminhamento.EncaminhamentoDetalheService;
import com.vozpublica.vozpublica.interacao.InteracaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/denuncias")
public class DenunciaController {

    private static final List<String> REGIOES = List.of(
            "RPA 1 - Centro", "RPA 2 - Norte", "RPA 3 - Noroeste",
            "RPA 4 - Oeste", "RPA 5 - Sudoeste", "RPA 6 - Sul");

    private final DenunciaService denunciaService;
    private final DenunciaRepository denunciaRepository;
    private final CategoriaRepository categoriaRepository;
    private final FotoRepository fotoRepository;
    private final InteracaoService interacaoService;
    private final ComentarioRepository comentarioRepository;
    private final DenunciaConsultaService consultaService;
    private final SimilaridadeService similaridadeService;
    private final HistoricoDetalheService detalheService;
    private final EncaminhamentoDetalheService encaminhamentoService;

    public DenunciaController(DenunciaService denunciaService,
                              DenunciaRepository denunciaRepository,
                              CategoriaRepository categoriaRepository,
                              FotoRepository fotoRepository,
                              InteracaoService interacaoService,
                              ComentarioRepository comentarioRepository,
                              DenunciaConsultaService consultaService,
                              SimilaridadeService similaridadeService,
                              HistoricoDetalheService detalheService,
                              EncaminhamentoDetalheService encaminhamentoService) {
        this.denunciaService = denunciaService;
        this.denunciaRepository = denunciaRepository;
        this.categoriaRepository = categoriaRepository;
        this.fotoRepository = fotoRepository;
        this.interacaoService = interacaoService;
        this.comentarioRepository = comentarioRepository;
        this.consultaService = consultaService;
        this.similaridadeService = similaridadeService;
        this.detalheService = detalheService;
        this.encaminhamentoService = encaminhamentoService;
    }

    @GetMapping
    public String listar(@ModelAttribute("filtro") FiltroDenuncia filtro,
                         BindingResult binding, // valores inválidos nos filtros são ignorados
                         @RequestParam(defaultValue = "0") int pagina,
                         Model model) {
        var resultado = consultaService.buscar(filtro, pagina);
        model.addAttribute("page", resultado.page());
        model.addAttribute("contadores", resultado.contadores());
        model.addAttribute("categorias", categoriaRepository.findByAtivaTrueOrderByNome());
        model.addAttribute("regioes", REGIOES);
        model.addAttribute("prioridades", Prioridade.values());
        model.addAttribute("statuses", StatusDenuncia.values());
        model.addAttribute("ordens", FiltroDenuncia.Ordem.values());
        return "denuncia-lista";
    }

    @GetMapping("/{id:\\d+}")
    public String detalhar(@PathVariable Long id, Authentication authentication, Model model) {
        Denuncia denuncia = denunciaRepository.findComDetalhesById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var detalhes = detalheService.carregar(id);

        model.addAttribute("d", denuncia);
        model.addAttribute("historico", detalhes.status());
        model.addAttribute("evidencias", detalhes.evidencias());
        model.addAttribute("historicoPrioridade", detalhes.prioridades());
        model.addAttribute("enc", encaminhamentoService.carregar(denuncia));
        model.addAttribute("fotos", fotoRepository.findByDenunciaIdOrderByIdAsc(id));
        model.addAttribute("autenticado", authentication != null);
        model.addAttribute("podeGerir", podeGerir(authentication));
        model.addAttribute("resumo", interacaoService.resumo(id,
                authentication != null ? authentication.getName() : null));
        model.addAttribute("comentarios", comentarioRepository.findByDenunciaIdOrderByDataCriacaoAsc(id));
        model.addAttribute("comentarioForm", new ComentarioForm());
        return "denuncia-detalhe";
    }

    @GetMapping("/similares")
    public String similares(@RequestParam(required = false) Long categoriaId,
                            @RequestParam(required = false) String bairro,
                            @RequestParam(required = false) String titulo,
                            @RequestParam(required = false) BigDecimal latitude,
                            @RequestParam(required = false) BigDecimal longitude,
                            Model model) {
        model.addAttribute("similares",
                similaridadeService.buscar(categoriaId, bairro, titulo, latitude, longitude));
        return "similares :: lista";
    }

    @GetMapping("/nova")
    public String formulario(Model model) {
        DenunciaForm form = new DenunciaForm();
        form.setPrioridadeSugerida(Prioridade.MEDIA);
        model.addAttribute("denunciaForm", form);
        carregarOpcoes(model);
        return "denuncia-nova";
    }

    @PostMapping("/nova")
    public String registrar(@Valid @ModelAttribute("denunciaForm") DenunciaForm form,
                            BindingResult result,
                            @RequestParam(name = "fotos", required = false) List<MultipartFile> fotos,
                            Authentication authentication,
                            Model model, RedirectAttributes redirect) {

        if ((form.getLatitude() == null) != (form.getLongitude() == null)) {
            result.rejectValue("latitude", "coordenadas",
                    "Informe latitude e longitude juntas, ou deixe as duas em branco.");
        }

        if (!result.hasErrors()) {
            try {
                Denuncia criada = denunciaService.registrar(form, authentication.getName(), fotos);
                redirect.addFlashAttribute("mensagem", "Ocorrência registrada com sucesso!");
                return "redirect:/denuncias/" + criada.getId();
            } catch (CategoriaInvalidaException e) {
                result.rejectValue("categoriaId", "invalida", e.getMessage());
            } catch (FotoInvalidaException e) {
                model.addAttribute("erroFotos", e.getMessage());
            }
        }

        carregarOpcoes(model);
        return "denuncia-nova";
    }

    private void carregarOpcoes(Model model) {
        model.addAttribute("categorias", categoriaRepository.findByAtivaTrueOrderByNome());
        model.addAttribute("regioes", REGIOES);
        model.addAttribute("prioridades", Prioridade.values());
    }

    /** Só controla se o link "Gerenciar" aparece. A permissão real é verificada no backend. */
    private static boolean podeGerir(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SERVIDOR")
                            || a.getAuthority().equals("ROLE_ADMINISTRADOR"));
    }
}