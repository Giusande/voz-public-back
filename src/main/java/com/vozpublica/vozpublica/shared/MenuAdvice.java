package com.vozpublica.vozpublica.shared;

import com.vozpublica.vozpublica.confirmacao.ReavaliacaoService;
import com.vozpublica.vozpublica.encaminhamento.AtrasoService;
import com.vozpublica.vozpublica.moderacao.SinalizacaoRepository;
import com.vozpublica.vozpublica.moderacao.StatusSinalizacao;
import com.vozpublica.vozpublica.notificacao.NotificacaoService;
import com.vozpublica.vozpublica.security.Perfis;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.function.Supplier;

@ControllerAdvice
public class MenuAdvice {

    public static final class Contador {
        private final Supplier<Long> origem;
        private Long cache;

        Contador(Supplier<Long> origem) {
            this.origem = origem;
        }

        public long getTotal() {
            if (cache == null) {
                try {
                    cache = origem.get();
                } catch (RuntimeException e) {
                    cache = 0L;
                }
            }
            return cache;
        }
    }

    private final NotificacaoService notificacaoService;
    private final AtrasoService atrasoService;
    private final ReavaliacaoService reavaliacaoService;
    private final SinalizacaoRepository sinalizacaoRepository;

    public MenuAdvice(NotificacaoService notificacaoService, AtrasoService atrasoService,
                      ReavaliacaoService reavaliacaoService, SinalizacaoRepository sinalizacaoRepository) {
        this.notificacaoService = notificacaoService;
        this.atrasoService = atrasoService;
        this.reavaliacaoService = reavaliacaoService;
        this.sinalizacaoRepository = sinalizacaoRepository;
    }

    @ModelAttribute
    public void menu(Authentication authentication, HttpServletRequest request, Model model) {
        boolean logado = authentication != null;
        boolean equipe = Perfis.ehEquipe(authentication);
        boolean admin = Perfis.ehAdministrador(authentication);

        model.addAttribute("navLogado", logado);
        model.addAttribute("navEquipe", equipe);
        model.addAttribute("navAdmin", admin);
        model.addAttribute("navCaminho", request.getRequestURI().substring(request.getContextPath().length()));

        if (logado) {
            model.addAttribute("navPapel", admin ? "Administrador" : equipe ? "Servidor" : "Cidadão");
            String email = authentication.getName();
            model.addAttribute("navNaoLidas", new Contador(() -> notificacaoService.naoLidas(email)));
        }
        if (equipe) {
            model.addAttribute("navAtrasadas", new Contador(() -> atrasoService.listar(0).getTotalElements()));
            model.addAttribute("navReavaliacao", new Contador(() -> (long) reavaliacaoService.listar().size()));
        }
        if (admin) {
            model.addAttribute("navModeracao",
                    new Contador(() -> sinalizacaoRepository.countByStatus(StatusSinalizacao.ABERTA)));
        }
    }
}