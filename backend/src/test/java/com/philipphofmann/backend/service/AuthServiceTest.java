package com.philipphofmann.backend.service;

import com.philipphofmann.backend.dto.AuthDtos.AuthResponse;
import com.philipphofmann.backend.entity.User;
import com.philipphofmann.backend.exception.AuthException;
import com.philipphofmann.backend.exception.EmailAlreadyExistsException;
import com.philipphofmann.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private com.philipphofmann.backend.config.JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_normalizesEmailAndSucceeds() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pw")).thenReturn("hash");
        when(tokenProvider.generateToken(any(), any())).thenReturn("tok");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AuthResponse response = authService.register(" A@B.com ", "pw");

        assertEquals("a@b.com", response.email());
        verify(userRepository).findByEmail("a@b.com");
        verify(userRepository).save(argThat(u -> "a@b.com".equals(u.getEmail())));
    }

    @Test
    void register_duplicateEmailThrows() {
        when(userRepository.findByEmail("a@b.com"))
                .thenReturn(Optional.of(User.builder().email("a@b.com").build()));

        // Muss die 409-typisierte Subklasse sein, damit der GlobalExceptionHandler
        // Conflict (nicht Unauthorized) zurueckgibt und der Nutzer nicht ausgeloggt wird.
        assertThrows(EmailAlreadyExistsException.class, () -> authService.register("a@b.com", "pw"));
    }

    @Test
    void login_wrongPasswordThrows() {
        User user = User.builder().email("a@b.com").passwordHash("hash").build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        assertThrows(AuthException.class, () -> authService.login("a@b.com", "bad"));
    }

    @Test
    void login_successSetsLastLogin() {
        User user = User.builder().email("a@b.com").passwordHash("hash").build();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(tokenProvider.generateToken(any(), any())).thenReturn("tok");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.login("a@b.com", "pw");

        assertNotNull(user.getLastLogin());
    }
}
