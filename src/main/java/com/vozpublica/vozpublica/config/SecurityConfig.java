package com.vozpublica.vozpublica.config;

import com.vozpublica.vozpublica.security.LoginSuccessHandler;
import com.vozpublica.vozpublica.security.SessaoAtualizadaFilter;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   LoginSuccessHandler loginSuccessHandler,
                                                   UserRepository userRepository) throws Exception {
        http
            .addFilterBefore(new SessaoAtualizadaFilter(userRepository), AuthorizationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/cadastro", "/acesso", "/error",
                 "/css/**", "/js/**", "/images/**",
                 "/favicon.ico", "/.well-known/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/denuncias", "/denuncias/{id:\\d+}",
                 "/fotos/{id:\\d+}", "/evidencias/{id:\\d+}", "/perfil/{id:\\d+}",
                 "/mapa", "/mapa/dados").permitAll()
                .requestMatchers("/gestao/**").hasAnyRole("SERVIDOR", "ADMINISTRADOR")
                .requestMatchers("/admin/**").hasRole("ADMINISTRADOR")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(loginSuccessHandler)
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/?logout")
                .permitAll())
            .csrf(Customizer.withDefaults());

        return http.build();
    }
}