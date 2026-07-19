package com.philipphofmann.backend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Creates and verifies JWT access tokens. Tokens carry the user's email as the
 * subject and the user id and role as custom claims.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    /**
     * Creates a signed JWT for the given user, valid for the configured lifetime.
     *
     * @param userId the user id stored in the {@code userId} claim
     * @param email  the user email used as the token subject
     * @param role   the user role stored in the {@code role} claim
     * @return the compact, signed JWT string
     */
    public String generateToken(UUID userId, String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000);
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Checks whether the token's signature is valid and it has not expired.
     *
     * @param token the JWT string
     * @return {@code true} if the token is valid
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Returns the email (subject) stored in the token.
     *
     * @param token the JWT string
     * @return the user email
     */
    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Returns the user id stored in the {@code userId} claim.
     *
     * @param token the JWT string
     * @return the user id
     */
    public UUID getUserId(String token) {
        return UUID.fromString(parseClaims(token).get("userId", String.class));
    }

    /**
     * Returns the role stored in the {@code role} claim.
     *
     * @param token the JWT string
     * @return the user role
     */
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * Verifies the signature and decodes the token payload.
     *
     * @param token the JWT string
     * @return the validated token claims
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
