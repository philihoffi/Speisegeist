package com.philipphofmann.backend.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenProvider tokenProvider;
    @InjectMocks private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_setsAuthentication() throws Exception {
        UUID userId = UUID.randomUUID();
        request.addHeader("Authorization", "Bearer valid-token");
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getUserId("valid-token")).thenReturn(userId);
        when(tokenProvider.getEmail("valid-token")).thenReturn("user@test.com");
        when(tokenProvider.getRole("valid-token")).thenReturn("USER");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        JwtAuthenticationFilter.AuthenticatedUser principal =
                (JwtAuthenticationFilter.AuthenticatedUser)
                        SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.email()).isEqualTo("user@test.com");
        assertThat(principal.userId()).isEqualTo(userId);
    }

    @Test
    void validAdminToken_addsAdminAuthority() throws Exception {
        request.addHeader("Authorization", "Bearer admin-token");
        when(tokenProvider.validateToken("admin-token")).thenReturn(true);
        when(tokenProvider.getUserId("admin-token")).thenReturn(UUID.randomUUID());
        when(tokenProvider.getEmail("admin-token")).thenReturn("admin@test.com");
        when(tokenProvider.getRole("admin-token")).thenReturn("ADMIN");

        filter.doFilter(request, response, chain);

        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertThat(authorities).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        assertThat(authorities).anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    void invalidToken_doesNotSetAuthentication() throws Exception {
        request.addHeader("Authorization", "Bearer bad-token");
        when(tokenProvider.validateToken("bad-token")).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenProvider, never()).getEmail(any());
    }

    @Test
    void noAuthHeader_doesNotSetAuthentication() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenProvider, never()).validateToken(any());
    }

    @Test
    void malformedHeader_doesNotSetAuthentication() throws Exception {
        request.addHeader("Authorization", "Basic abc123");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenProvider, never()).validateToken(any());
    }

    @Test
    void filterChainIsContinuedRegardlessOfToken() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
