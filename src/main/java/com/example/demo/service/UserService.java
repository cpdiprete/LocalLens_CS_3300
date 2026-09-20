package com.example.demo.service;

import com.example.demo.model.AppUser;
import com.example.demo.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user, storing only a salted BCrypt hash of the password.
     *
     * @throws DuplicateUsernameException if the username is already registered
     */
    @Transactional
    public AppUser register(String username, String rawPassword) {
        String normalized = username == null ? "" : username.trim();
        if (userRepository.existsByUsername(normalized)) {
            throw new DuplicateUsernameException(normalized);
        }
        return userRepository.save(new AppUser(normalized, passwordEncoder.encode(rawPassword)));
    }
}
