package com.nexus.ingress.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;

/**
 * Validates JWT Bearer tokens for NIST SP 800-207 Zero-Trust Identity Verification.
 */
@Component
public class JwtValidator {

    private final Key signingKey;

    public JwtValidator(@Value("${nexus.security.jwt.secret:nexus-gateway-default-secret-key-32-bytes-long!}") String secretKey) {
        // Ensure secret key is at least 256 bits (32 bytes) for HMAC-SHA256
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractSubject(String token) {
        Claims claims = getClaims(token);
        return claims != null ? claims.getSubject() : null;
    }

    public String extractTenantId(String token) {
        Claims claims = getClaims(token);
        if (claims != null) {
            Object tenantObj = claims.get("tenant_id");
            if (tenantObj != null) {
                return tenantObj.toString();
            }
        }
        return "default-tenant";
    }

    public Claims getClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token.trim())
                    .getBody();
        } catch (Exception e) {
            return null;
        }
    }

    public String generateTestToken(String subject, String tenantId) {
        return Jwts.builder()
                .setSubject(subject)
                .claim("tenant_id", tenantId)
                .setIssuedAt(new java.util.Date())
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 3600 * 1000))
                .signWith(signingKey)
                .compact();
    }
}
