package com.vozpublica.vozpublica.security;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.Locale;

public final class ChavesLogin {

    public static final int LIMITE_PAR = 5;
    public static final int LIMITE_IP = 20;
    public static final Duration JANELA = Duration.ofMinutes(15);
    public static final Duration BLOQUEIO = Duration.ofMinutes(15);

    private ChavesLogin() {}

    public static String emailDe(HttpServletRequest request) {
        String u = request.getParameter("username");
        if (u == null) return "";
        u = u.trim().toLowerCase(Locale.ROOT);
        return u.length() > 150 ? u.substring(0, 150) : u;
    }

    public static String ip(String ip) {
        return "login-ip:" + ip;
    }

    public static String par(String email, String ip) {
        return "login:" + ip + "|" + email;
    }
}