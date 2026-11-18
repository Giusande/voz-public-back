package com.vozpublica.vozpublica.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Envia e-mails em segundo plano (a resposta ao usuário não depende do envio, o que também
 * evita revelar, pelo tempo de resposta, se uma conta existe). Falhas só vão para o log.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> remetente;
    private final String de;
    private final String baseUrl;
    private final boolean logarConteudo;

    public EmailService(ObjectProvider<JavaMailSender> remetente,
                        @Value("${app.mail.from:nao-responder@vozpublica.local}") String de,
                        @Value("${app.base-url:http://localhost:8080}") String baseUrl,
                        @Value("${app.mail.log-conteudo:false}") boolean logarConteudo) {
        this.remetente = remetente;
        this.de = de;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.logarConteudo = logarConteudo;
    }

    @Async
    public void enviarVerificacao(String destino, String nome, String token) {
        enviar(destino, "Voz Pública: confirme o seu e-mail",
                "Olá, " + nome + "!\n\n"
                + "Para ativar a sua conta no Voz Pública, confirme o seu e-mail pelo link abaixo "
                + "(vale por 24 horas):\n\n"
                + baseUrl + "/verificar-email?token=" + token + "\n\n"
                + "Se você não criou essa conta, ignore esta mensagem.");
    }

    @Async
    public void enviarRecuperacao(String destino, String nome, String token) {
        enviar(destino, "Voz Pública: redefinição de senha",
                "Olá, " + nome + "!\n\n"
                + "Recebemos um pedido para redefinir a senha da sua conta. Use o link abaixo "
                + "(vale por 1 hora e só pode ser usado uma vez):\n\n"
                + baseUrl + "/redefinir-senha?token=" + token + "\n\n"
                + "Se não foi você, ignore esta mensagem: a sua senha continua a mesma.");
    }

    private void enviar(String destino, String assunto, String texto) {
        JavaMailSender sender = remetente.getIfAvailable();
        if (sender == null) {
            if (logarConteudo) {
                log.warn("SMTP não configurado; e-mail NÃO enviado (modo desenvolvimento).\nPara: {}\nAssunto: {}\n{}",
                        destino, assunto, texto);
            } else {
                log.warn("SMTP não configurado; e-mail '{}' não foi enviado.", assunto);
            }
            return;
        }
        try {
            SimpleMailMessage mensagem = new SimpleMailMessage();
            mensagem.setFrom(de);
            mensagem.setTo(destino);
            mensagem.setSubject(assunto);
            mensagem.setText(texto);
            sender.send(mensagem);
        } catch (Exception e) {
            log.error("Falha ao enviar o e-mail '{}' ({}).", assunto, e.getClass().getSimpleName());
        }
    }
}