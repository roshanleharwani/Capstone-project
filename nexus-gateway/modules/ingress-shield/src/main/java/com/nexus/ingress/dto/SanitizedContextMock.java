package com.nexus.ingress.dto;

import com.nexus.ingress.masking.DataClassification;

import java.util.Map;

/**
 * Module-local representation of Member 1 (ingress-shield) output context.
 * Passed to downstream modules (Context Memory, Quota Engine, Router).
 */
public class SanitizedContextMock {

    private String sessionId;
    private String tenantId;
    private ChatCompletionRequest sanitizedRequest;
    private Map<String, String> pseudonymMapping;
    private DataClassification classification;
    private boolean hardRouteTier1;
    private Map<String, String> securityMetadata;

    public SanitizedContextMock() {
    }

    public SanitizedContextMock(String sessionId, String tenantId, ChatCompletionRequest sanitizedRequest,
                                Map<String, String> pseudonymMapping, DataClassification classification,
                                boolean hardRouteTier1, Map<String, String> securityMetadata) {
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.sanitizedRequest = sanitizedRequest;
        this.pseudonymMapping = pseudonymMapping;
        this.classification = classification;
        this.hardRouteTier1 = hardRouteTier1;
        this.securityMetadata = securityMetadata;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public ChatCompletionRequest getSanitizedRequest() {
        return sanitizedRequest;
    }

    public void setSanitizedRequest(ChatCompletionRequest sanitizedRequest) {
        this.sanitizedRequest = sanitizedRequest;
    }

    public Map<String, String> getPseudonymMapping() {
        return pseudonymMapping;
    }

    public void setPseudonymMapping(Map<String, String> pseudonymMapping) {
        this.pseudonymMapping = pseudonymMapping;
    }

    public DataClassification getClassification() {
        return classification;
    }

    public void setClassification(DataClassification classification) {
        this.classification = classification;
    }

    public boolean isHardRouteTier1() {
        return hardRouteTier1;
    }

    public void setHardRouteTier1(boolean hardRouteTier1) {
        this.hardRouteTier1 = hardRouteTier1;
    }

    public Map<String, String> getSecurityMetadata() {
        return securityMetadata;
    }

    public void setSecurityMetadata(Map<String, String> securityMetadata) {
        this.securityMetadata = securityMetadata;
    }
}
