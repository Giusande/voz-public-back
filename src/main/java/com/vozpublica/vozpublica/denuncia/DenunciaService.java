package com.vozpublica.vozpublica.denuncia;

import com.vozpublica.vozpublica.categoria.Categoria;
import com.vozpublica.vozpublica.categoria.CategoriaRepository;
import com.vozpublica.vozpublica.denuncia.ArmazenamentoFotoService.FotoProcessada;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class DenunciaService {

    private final DenunciaRepository denunciaRepository;
    private final HistoricoStatusRepository historicoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UserRepository userRepository;
    private final FotoRepository fotoRepository;
    private final ArmazenamentoFotoService armazenamento;

    public DenunciaService(DenunciaRepository denunciaRepository,
                           HistoricoStatusRepository historicoRepository,
                           CategoriaRepository categoriaRepository,
                           UserRepository userRepository,
                           FotoRepository fotoRepository,
                           ArmazenamentoFotoService armazenamento) {
        this.denunciaRepository = denunciaRepository;
        this.historicoRepository = historicoRepository;
        this.categoriaRepository = categoriaRepository;
        this.userRepository = userRepository;
        this.fotoRepository = fotoRepository;
        this.armazenamento = armazenamento;
    }

    @Transactional
    public Denuncia registrar(DenunciaForm form, String emailAutor, List<MultipartFile> fotos) {
        // 1) valida as imagens antes de tocar no banco ou no disco
        List<FotoProcessada> processadas = armazenamento.validar(fotos);

        User autor = userRepository.findByEmail(emailAutor.trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));

        Categoria categoria = categoriaRepository.findById(form.getCategoriaId())
                .filter(Categoria::isAtiva)
                .orElseThrow(CategoriaInvalidaException::new);

        String endereco = form.getEndereco() == null || form.getEndereco().isBlank()
                ? null : form.getEndereco().trim();

        Denuncia denuncia = denunciaRepository.save(new Denuncia(
                autor, categoria,
                form.getTitulo().trim(), form.getDescricao().trim(),
                endereco, form.getBairro().trim(), form.getRegiao(),
                form.getLatitude(), form.getLongitude(),
                form.getPrioridadeSugerida()));

        historicoRepository.save(new HistoricoStatus(
                denuncia, null, StatusDenuncia.RECEBIDA, autor,
                "Ocorrência registrada pelo cidadão."));

        // 2) grava os arquivos; se a transação for desfeita, apaga todos
        for (FotoProcessada processada : processadas) {
            String nome = armazenamento.salvar(processada);
            apagarSeTransacaoFalhar(nome);
            fotoRepository.save(new Foto(denuncia, nome));
        }

        return denuncia;
    }

    private void apagarSeTransacaoFalhar(String nomeArquivo) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    armazenamento.apagar(nomeArquivo);
                }
            }
        });
    }
}