package com.philipphofmann.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Validates the JWT on every incoming request and populates the Spring
 * {@code SecurityContext} with an {@link AuthenticatedUser} principal so that
 * downstream services can scope their queries by the authenticated user.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    /**
     * Extracts the bearer token from the request, validates it, and on success
     * builds an authentication object. The principal is the {@link AuthenticatedUser}
     * (carrying the userId) so that downstream services can scope their queries per user.
     *
     * @param request     the incoming HTTP request
     * @param response    the outgoing HTTP response
     * @param filterChain the remaining filter chain
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && tokenProvider.validateToken(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            UUID userId = tokenProvider.getUserId(token);
            String email = tokenProvider.getEmail(token);
            String role = tokenProvider.getRole(token);

            AuthenticatedUser principal = new AuthenticatedUser(userId, email, role);
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            if ("ADMIN".equals(role)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Reads the bearer token from the {@code Authorization} header.
     *
     * @param request the incoming HTTP request
     * @return the raw token without the {@code "Bearer "} prefix, or {@code null} if absent
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    /**
     * Authenticated principal carrying the user identity extracted from the JWT.
     *
     * @param userId the authenticated user's id
     * @param email  the authenticated user's email
     * @param role   the authenticated user's role
     */
    public record AuthenticatedUser(UUID userId, String email, String role) {
    }
}
