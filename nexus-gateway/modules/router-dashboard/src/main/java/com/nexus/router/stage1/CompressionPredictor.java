package com.nexus.router.stage1;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CompressionPredictor {

    /**
     * Stage 1: Predicts the compression ratio (rho).
     * The ratio indicates how much the prompt can be compressed without losing essential context.
     * Higher ratio means more compressability.
     * 
     * @param payload The request payload containing prompt length, task type, etc.
     * @return predicted compression ratio between 0.0 and 1.0
     */
    public double predictCompressionRatio(Map<String, Object> payload) {
        // Dummy heuristic: base it on prompt length
        String prompt = (String) payload.getOrDefault("prompt", "");
        
        if (prompt.length() < 50) {
            return 0.0; // Too short to compress
        }
        
        // Example feature checks
        boolean isMathOrCode = prompt.contains("code") || prompt.contains("math");
        if (isMathOrCode) {
            return 0.2; // Math/code shouldn't be highly compressed
        }

        // Default moderate compression
        return 0.5;
    }
}
