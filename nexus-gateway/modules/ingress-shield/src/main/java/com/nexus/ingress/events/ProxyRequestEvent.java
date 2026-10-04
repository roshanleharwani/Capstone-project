package com.nexus.ingress.events;

import java.util.Map;

public class ProxyRequestEvent {
    private final String tenantId;
    private final int piiInterceptedCount;
    private final boolean isTier1Routed;
    private final String eventLogMessage;
    private final boolean isCacheHit;
    private final long tokensTraded;
    private final Map<String, Integer> piiBreakdown;

    public ProxyRequestEvent(String tenantId, int piiInterceptedCount, boolean isTier1Routed, String eventLogMessage, boolean isCacheHit, long tokensTraded, Map<String, Integer> piiBreakdown) {
        this.tenantId = tenantId;
        this.piiInterceptedCount = piiInterceptedCount;
        this.isTier1Routed = isTier1Routed;
        this.eventLogMessage = eventLogMessage;
        this.isCacheHit = isCacheHit;
        this.tokensTraded = tokensTraded;
        this.piiBreakdown = piiBreakdown;
    }

    public String getTenantId() {
        return tenantId;
    }

    public int getPiiInterceptedCount() {
        return piiInterceptedCount;
    }

    public boolean isTier1Routed() {
        return isTier1Routed;
    }

    public String getEventLogMessage() {
        return eventLogMessage;
    }

    public boolean isCacheHit() {
        return isCacheHit;
    }

    public long getTokensTraded() {
        return tokensTraded;
    }

    public Map<String, Integer> getPiiBreakdown() {
        return piiBreakdown;
    }
}
