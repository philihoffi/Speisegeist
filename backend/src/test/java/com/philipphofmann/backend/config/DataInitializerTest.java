package com.philipphofmann.backend.config;

import com.philipphofmann.backend.entity.User;
import com.philipphofmann.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataInitializerTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private DataInitializer initializer;

    private void configure(String email, String password) {
        ReflectionTestUtils.setField(initializer, "adminEmail", email);
        ReflectionTestUtils.setField(initializer, "adminPassword", password);
    }

    @Test
    void createsAdminWhenNoUsersExist() {
        configure("Admin@Example.COM", "secret");
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");

        initializer.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void normalizesEmailToLowercaseAndTrims() {
        configure("  Admin@Example.COM  ", "secret");
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        initializer.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("admin@example.com");
    }

    @Test
    void neverStoresPlaintextPassword() {
        configure("admin@example.com", "supergeheim");
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("supergeheim")).thenReturn("$2a$10$hash");

        initializer.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("supergeheim");
        verify(passwordEncoder).encode("supergeheim");
    }

    @Test
    void skipsWhenUsersAlreadyExist() {
        configure("admin@example.com", "secret");
        when(userRepository.count()).thenReturn(3L);

        initializer.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void skipsWhenEmailIsNull() {
        configure(null, "secret");

        initializer.run();

        verify(userRepository, never()).count();
        verify(userRepository, never()).save(any());
    }

    @Test
    void skipsWhenPasswordIsNull() {
        configure("admin@example.com", null);

        initializer.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void skipsWhenEmailIsBlank() {
        configure("   ", "secret");

        initializer.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void skipsWhenPasswordIsBlank() {
        configure("admin@example.com", "  ");

        initializer.run();

        verify(userRepository, never()).save(any());
    }
}
