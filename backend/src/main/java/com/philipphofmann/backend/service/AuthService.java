package com.philipphofmann.backend.service;

import com.philipphofmann.backend.config.JwtTokenProvider;
import com.philipphofmann.backend.dto.AuthDtos.AuthResponse;
import com.philipphofmann.backend.entity.User;
import com.philipphofmann.backend.exception.AuthException;
import com.philipphofmann.backend.exception.EmailAlreadyExistsException;
import com.philipphofmann.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Handles user registration and login, including password hashing, email
 * normalization, and JWT issuance.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    /**
     * Registers a new user, hashing the password, and returns a JWT.
     *
     * @param email    the desired login email
     * @param password the raw password
     * @return the auth response with a signed JWT
     */
    @Transactional
    public AuthResponse register(String email, String password) {
        email = normalizeEmail(email);
        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyExistsException("E-Mail ist bereits registriert");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .build();
        user = userRepository.save(user);

        String token = tokenProvider.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, user.getEmail());
    }

    /**
     * Authenticates a user and returns a JWT, updating the last-login timestamp.
     *
     * @param email    the login email
     * @param password the raw password
     * @return the auth response with a signed JWT
     */
    @Transactional
    public AuthResponse login(String email, String password) {
        email = normalizeEmail(email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("E-Mail oder Passwort falsch"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthException("E-Mail oder Passwort falsch");
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = tokenProvider.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, user.getEmail());
    }

    /**
     * Normalizes an email for lookups: trims whitespace and lowercases it.
     *
     * @param email the raw email
     * @return the normalized email, or {@code null} if the input was {@code null}
     */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
