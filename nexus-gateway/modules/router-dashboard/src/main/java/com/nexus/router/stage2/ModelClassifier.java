package com.nexus.router.stage2;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ModelClassifier {

    /**
     * Stage 2: BEST-Route style multi-head classifier.
     * Chooses between Tier 1 (vLLM self-hosted) and Tier 2 (Proprietary APIs) 
     * based on the predicted compression ratio and user constraints.
     * 
     * @param payload Request payload (including tier constraints)
     * @param compressionRatio Predicted compression ratio from Stage 1
     * @return "TIER1" or "TIER2"
     */
    public String classifyModelTier(Map<String, Object> payload, double compressionRatio) {
        String dataClassification = (String) payload.getOrDefault("dataClassification", "PUBLIC");
        
        // Rule: Restricted data must always be Hard-Routed to Tier 1
        if ("RESTRICTED".equalsIgnoreCase(dataClassification)) {
            return "TIER1";
        }
        
        // If high compression is possible, we can save tokens on Tier 2 APIs
        if (compressionRatio > 0.4) {
            return "TIER2";
        }
        
        // Default to self-hosted Tier 1 to save costs
        return "TIER1";
    }
}
