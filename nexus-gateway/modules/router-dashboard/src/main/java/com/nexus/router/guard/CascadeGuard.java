package com.nexus.router.guard;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CascadeGuard {

    private static final double CONFIDENCE_THRESHOLD = 0.85;

    /**
     * Fast-Path Bypass for trivially short queries.
     * 
     * @param payload The request payload
     * @return true if the query should bypass complex routing
     */
    public boolean shouldFastPath(Map<String, Object> payload) {
        String prompt = (String) payload.getOrDefault("prompt", "");
        return prompt.length() < 30; // Very short queries
    }

    /**
     * Post-hoc reliability evaluation (FrugalGPT style).
     * Evaluates if the answer is reliable enough to be returned.
     * 
     * @param response The response from the LLM model
     * @return true if reliable, false if escalation is needed
     */
    public boolean evaluateReliability(Map<String, Object> response) {
        // In a real implementation, this would use a small proxy model
        // to evaluate g(q, a). Here we simulate with a dummy check.
        Double confidenceScore = (Double) response.getOrDefault("confidence", 0.9);
        return confidenceScore >= CONFIDENCE_THRESHOLD;
    }
}
