package com.vozpublica.vozpublica.comentario;

import com.vozpublica.vozpublica.denuncia.DenunciaNaoEncontradaException;
import com.vozpublica.vozpublica.denuncia.DenunciaRepository;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComentarioService {

    private final ComentarioRepository comentarioRepository;
    private final DenunciaRepository denunciaRepository;
    private final UserRepository userRepository;

    public ComentarioService(ComentarioRepository comentarioRepository,
                             DenunciaRepository denunciaRepository,
                             UserRepository userRepository) {
        this.comentarioRepository = comentarioRepository;
        this.denunciaRepository = denunciaRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Comentario adicionar(Long denunciaId, String emailAutor, String conteudo) {
        if (!denunciaRepository.existsById(denunciaId)) {
            throw new DenunciaNaoEncontradaException();
        }
        User autor = userRepository.findByEmail(emailAutor.trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
        return comentarioRepository.save(new Comentario(denunciaId, autor, conteudo.trim()));
    }
}