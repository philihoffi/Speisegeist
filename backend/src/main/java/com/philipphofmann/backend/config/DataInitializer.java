package com.philipphofmann.backend.config;

import com.philipphofmann.backend.entity.User;
import com.philipphofmann.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates a default admin user on first startup if no users exist yet.
 * Credentials are read from environment variables so they are never hardcoded.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:#{null}}")
    private String adminEmail;

    @Value("${admin.password:#{null}}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.debug("Keine Default-Admin-Umgebungsvariablen gesetzt — überspringe.");
            return;
        }
        if (userRepository.count() > 0) {
            log.debug("Es existieren bereits Benutzer — Default-Admin wird nicht angelegt.");
            return;
        }

        User admin = User.builder()
                .email(adminEmail.trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(User.Role.ADMIN)
                .build();
        userRepository.save(admin);
        log.info("Default-Admin angelegt: {}", adminEmail);
    }
}