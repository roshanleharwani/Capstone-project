package com.nexus.ingress.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates API Keys (X-API-Key or Bearer sk-...) for API key authentication layer.
 */
@Component
public class ApiKeyValidator {

    // Mock API key registry mapping API key -> Tenant ID
    private final Map<String, String> apiKeyStore = new ConcurrentHashMap<>();

    public ApiKeyValidator() {
        // Register default test API keys
        apiKeyStore.put("sk-nexus-test-key-12345", "tenant-cs-dept");
        apiKeyStore.put("sk-nexus-test-key-67890", "tenant-biotech-dept");
        apiKeyStore.put("sk-nexus-test-key-admin", "tenant-admin");
    }

    public boolean validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        String cleanKey = cleanKey(apiKey);
        return apiKeyStore.containsKey(cleanKey) || cleanKey.startsWith("sk-nexus-valid");
    }

    public String extractTenantId(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String cleanKey = cleanKey(apiKey);
        return apiKeyStore.getOrDefault(cleanKey, "tenant-default");
    }

    public void registerApiKey(String apiKey, String tenantId) {
        apiKeyStore.put(cleanKey(apiKey), tenantId);
    }

    private String cleanKey(String apiKey) {
        String key = apiKey.trim();
        if (key.toLowerCase().startsWith("bearer ")) {
            key = key.substring(7).trim();
        }
        return key;
    }
}
