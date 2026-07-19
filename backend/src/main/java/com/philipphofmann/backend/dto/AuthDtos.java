package com.philipphofmann.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request and response records for authentication endpoints.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** Registration request payload. */
    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, message = "Passwort muss mindestens 8 Zeichen lang sein") String password) {
    }

    /** Login request payload. */
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    /** Authentication response carrying the JWT, email, and role. */
    public record AuthResponse(String token, String email, String role) {
    }
}
