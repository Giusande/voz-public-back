package com.vozpublica.vozpublica.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ContaProps props;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, ContaProps props) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Transactional
    public User register(RegisterForm form) {
        String email = form.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }

        User user = new User(
                form.getName().trim(),
                email,
                passwordEncoder.encode(form.getPassword()),
                Role.CIDADAO);
        if (props.isVerificacaoObrigatoria()) {
            user.exigirVerificacaoEmail();
        }

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException();
        }
    }
}