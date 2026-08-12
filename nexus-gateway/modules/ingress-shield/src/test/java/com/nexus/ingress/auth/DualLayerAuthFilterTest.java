package com.nexus.ingress.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DualLayerAuthFilterTest {

    private JwtValidator jwtValidator;
    private ApiKeyValidator apiKeyValidator;
    private MTLSBoundaryEnforcer mtlsBoundaryEnforcer;
    private DualLayerAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtValidator = new JwtValidator("nexus-gateway-default-secret-key-32-bytes-long!");
        apiKeyValidator = new ApiKeyValidator();
        mtlsBoundaryEnforcer = new MTLSBoundaryEnforcer();
        mtlsBoundaryEnforcer.setEnforceMtls(false); // Allow requests in test

        filter = new DualLayerAuthFilter(jwtValidator, apiKeyValidator, mtlsBoundaryEnforcer);
    }

    @Test
    @DisplayName("Should authenticate request with valid JWT Bearer token")
    void testValidJwtAuth() throws ServletException, IOException {
        String token = jwtValidator.generateTestToken("user-123", "tenant-cs-dept");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/chat/completions");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertEquals("tenant-cs-dept", request.getAttribute("X-Tenant-ID"));
    }

    @Test
    @DisplayName("Should authenticate request with valid API Key header")
    void testValidApiKeyAuth() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/chat/completions");
        request.addHeader("X-API-Key", "sk-nexus-test-key-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertEquals("tenant-cs-dept", request.getAttribute("X-Tenant-ID"));
    }

    @Test
    @DisplayName("Should reject request with missing authentication (401 Unauthorized)")
    void testMissingAuthRejection() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/chat/completions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
    }
}
