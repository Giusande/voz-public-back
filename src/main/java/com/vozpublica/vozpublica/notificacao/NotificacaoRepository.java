package com.vozpublica.vozpublica.notificacao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    Page<Notificacao> findByUsuarioIdOrderByIdDesc(Long usuarioId, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notificacao n, User u "
            + "WHERE u.email = :email AND n.usuarioId = u.id AND n.lida = false")
    long contarNaoLidas(@Param("email") String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notificacao n SET n.lida = true "
            + "WHERE n.id = :id AND n.usuarioId = :usuarioId AND n.lida = false")
    int marcarLida(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notificacao n SET n.lida = true WHERE n.usuarioId = :usuarioId AND n.lida = false")
    int marcarTodas(@Param("usuarioId") Long usuarioId);
}