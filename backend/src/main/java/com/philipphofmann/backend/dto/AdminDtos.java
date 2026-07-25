package com.philipphofmann.backend.dto;

/**
 * Request and response records for admin endpoints.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** Lightweight user representation for the admin user list. */
    public record UserListItem(
            java.util.UUID id,
            String email,
            String role,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime lastLogin) {
    }

    /** Request payload to change a user's role. */
    public record UpdateRoleRequest(com.philipphofmann.backend.entity.User.Role role) {
    }

    /** Database and API statistics for the admin dashboard. */
    public record StatsResponse(
            long userCount,
            long recipeCount,
            long ingredientCount,
            long ingredientImageCount,
            long recipeImageCount,
            OpenRouterKeyInfo openRouterKey) {
    }

    /** OpenRouter API key metadata returned by /auth/key. */
    public record OpenRouterKeyInfo(
            String label,
            Double usageCredits,
            Double limitCredits,
            boolean isFreeTier,
            Integer rateLimitRequests,
            String rateLimitInterval,
            String error) {
    }
}