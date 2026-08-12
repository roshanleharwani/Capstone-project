package com.nexus.ingress.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Spring Security Filter executing Dual-Layer Authentication (JWT + API Key)
 * and mTLS pod boundary checks.
 */
@Component
public class DualLayerAuthFilter extends OncePerRequestFilter {

    private final JwtValidator jwtValidator;
    private final ApiKeyValidator apiKeyValidator;
    private final MTLSBoundaryEnforcer mtlsBoundaryEnforcer;

    @Value("${nexus.security.require-dual-auth:false}")
    private boolean requireDualAuth;

    public DualLayerAuthFilter(JwtValidator jwtValidator, ApiKeyValidator apiKeyValidator, MTLSBoundaryEnforcer mtlsBoundaryEnforcer) {
        this.jwtValidator = jwtValidator;
        this.apiKeyValidator = apiKeyValidator;
        this.mtlsBoundaryEnforcer = mtlsBoundaryEnforcer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Skip auth for public endpoints if any (e.g. actuator/health)
        String path = request.getRequestURI();
        if (path.contains("/actuator") || path.contains("/health") || path.contains("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. Enforce mTLS Pod Boundary check
        if (!mtlsBoundaryEnforcer.validateMTLSBoundary(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":{\"message\":\"mTLS Pod Boundary Verification Failed\",\"type\":\"invalid_client_certificate\",\"code\":\"mtls_forbidden\"}}");
            return;
        }

        // 2. Extract Auth Credentials
        String authHeader = request.getHeader("Authorization");
        String apiKeyHeader = request.getHeader("X-API-Key");

        boolean jwtValid = false;
        boolean apiKeyValid = false;
        String principal = null;
        String tenantId = request.getHeader("X-Tenant-ID");

        // Check JWT token
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (jwtValidator.validateToken(token)) {
                jwtValid = true;
                principal = jwtValidator.extractSubject(token);
                if (tenantId == null) {
                    tenantId = jwtValidator.extractTenantId(token);
                }
            } else if (apiKeyValidator.validateApiKey(token)) {
                // Bearer token could be an API Key starting with sk-
                apiKeyValid = true;
                if (principal == null) principal = "api-key-user";
                if (tenantId == null) tenantId = apiKeyValidator.extractTenantId(token);
            }
        }

        // Check API Key Header
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            if (apiKeyValidator.validateApiKey(apiKeyHeader)) {
                apiKeyValid = true;
                if (principal == null) principal = "api-key-user";
                if (tenantId == null) tenantId = apiKeyValidator.extractTenantId(apiKeyHeader);
            }
        }

        // Evaluate Dual Authentication policy
        boolean isAuthenticated;
        if (requireDualAuth) {
            isAuthenticated = jwtValid && apiKeyValid;
        } else {
            isAuthenticated = jwtValid || apiKeyValid;
        }

        if (isAuthenticated) {
            if (tenantId == null) tenantId = "default-tenant";
            request.setAttribute("X-Tenant-ID", tenantId);
            request.setAttribute("X-User-Principal", principal != null ? principal : "authenticated-user");

            List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    principal != null ? principal : "authenticated-user", null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authToken);

            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":{\"message\":\"Invalid or missing authentication credentials (JWT / API Key required).\",\"type\":\"invalid_authentication\",\"code\":\"unauthorized\"}}");
        }
    }

    public void setRequireDualAuth(boolean requireDualAuth) {
        this.requireDualAuth = requireDualAuth;
    }
}
