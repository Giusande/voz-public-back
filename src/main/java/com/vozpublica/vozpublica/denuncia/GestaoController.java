package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.encaminhamento.EncaminhamentoDetalheService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/gestao/denuncias/{id:\\d+}")
public class GestaoController {

    private final DenunciaRepository denunciaRepository;
    private final GestaoDenunciaService gestaoService;
    private final HistoricoDetalheService detalheService;
    private final EncaminhamentoDetalheService encaminhamentoService;

    public GestaoController(DenunciaRepository denunciaRepository,
                            GestaoDenunciaService gestaoService,
                            HistoricoDetalheService detalheService,
                            EncaminhamentoDetalheService encaminhamentoService) {
        this.denunciaRepository = denunciaRepository;
        this.gestaoService = gestaoService;
        this.detalheService = detalheService;
        this.encaminhamentoService = encaminhamentoService;
    }

    @GetMapping
    public String painel(@PathVariable Long id, Model model) {
        Denuncia d = denunciaRepository.findComDetalhesById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var detalhes = detalheService.carregar(id);

        model.addAttribute("d", d);
        model.addAttribute("proximos", d.getStatus().proximos());
        model.addAttribute("prioridades", Prioridade.values());
        model.addAttribute("historico", detalhes.status());
        model.addAttribute("evidencias", detalhes.evidencias());
        model.addAttribute("historicoPrioridade", detalhes.prioridades());
        model.addAttribute("enc", encaminhamentoService.carregar(d));
        model.addAttribute("opcoes", encaminhamentoService.opcoes());
        return "gestao-denuncia";
    }

    @PostMapping("/status")
    public String alterarStatus(@PathVariable Long id,
                                @RequestParam(required = false) StatusDenuncia novoStatus,
                                @RequestParam(required = false) String observacao,
                                @RequestParam(name = "evidencias", required = false) List<MultipartFile> evidencias,
                                Authentication authentication,
                                RedirectAttributes redirect) {
        try {
            gestaoService.alterarStatus(id, novoStatus, observacao, evidencias, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Situação atualizada.");
        } catch (GestaoInvalidaException | FotoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/gestao/denuncias/" + id;
    }

    @PostMapping("/prioridade")
    public String definirPrioridade(@PathVariable Long id,
                                    @RequestParam(required = false) Prioridade prioridade,
                                    @RequestParam(required = false) String observacao,
                                    Authentication authentication,
                                    RedirectAttributes redirect) {
        try {
            gestaoService.definirPrioridadeOficial(id, prioridade, observacao, authentication.getName());
            redirect.addFlashAttribute("mensagem", "Prioridade oficial atualizada.");
        } catch (GestaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/gestao/denuncias/" + id;
    }
}