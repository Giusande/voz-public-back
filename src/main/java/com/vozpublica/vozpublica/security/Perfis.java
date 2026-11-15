package com.vozpublica.vozpublica.security;

import org.springframework.security.core.Authentication;

public final class Perfis {

    private Perfis() {}

    public static boolean ehAdministrador(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));
    }
}