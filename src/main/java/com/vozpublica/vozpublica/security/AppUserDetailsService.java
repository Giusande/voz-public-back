package com.vozpublica.vozpublica.security;

import com.vozpublica.vozpublica.user.ContaProps;
import com.vozpublica.vozpublica.user.User;
import com.vozpublica.vozpublica.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final ContaProps props;

    public AppUserDetailsService(UserRepository userRepository, ContaProps props) {
        this.userRepository = userRepository;
        this.props = props;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        boolean bloqueada = !user.isEnabled()
                || (props.isVerificacaoObrigatoria() && !user.isEmailVerificado());

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .disabled(bloqueada)
                .build();
    }
}