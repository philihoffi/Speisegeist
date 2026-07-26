package com.philipphofmann.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        // Mindestens 32 Zeichen für HMAC-SHA256
        provider = new JwtTokenProvider("test-secret-key-must-be-32-chars!", 3600);
    }

    @Test
    void generateToken_canBeValidated() {
        String token = provider.generateToken(UUID.randomUUID(), "user@test.de", "USER");
        assertTrue(provider.validateToken(token));
    }

    @Test
    void getEmail_returnsSubject() {
        String token = provider.generateToken(UUID.randomUUID(), "user@test.de", "USER");
        assertEquals("user@test.de", provider.getEmail(token));
    }

    @Test
    void getUserId_roundtrips() {
        UUID id = UUID.randomUUID();
        String token = provider.generateToken(id, "user@test.de", "USER");
        assertEquals(id, provider.getUserId(token));
    }

    @Test
    void getRole_returnsRole() {
        String token = provider.generateToken(UUID.randomUUID(), "admin@test.de", "ADMIN");
        assertEquals("ADMIN", provider.getRole(token));
    }

    @Test
    void validateToken_expiredTokenReturnsFalse() {
        // Ablaufzeit 0 Sekunden → sofort abgelaufen
        JwtTokenProvider shortLived = new JwtTokenProvider("test-secret-key-must-be-32-chars!", 0);
        String token = shortLived.generateToken(UUID.randomUUID(), "user@test.de", "USER");
        assertFalse(shortLived.validateToken(token));
    }

    @Test
    void validateToken_tamperedTokenReturnsFalse() {
        String token = provider.generateToken(UUID.randomUUID(), "user@test.de", "USER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(provider.validateToken(tampered));
    }

    @Test
    void validateToken_emptyStringReturnsFalse() {
        assertFalse(provider.validateToken(""));
    }
}
