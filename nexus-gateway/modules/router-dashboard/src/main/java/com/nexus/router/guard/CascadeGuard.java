package com.nexus.router.guard;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CascadeGuard {

    private static final double CONFIDENCE_THRESHOLD = 0.85;

    // Common refusal patterns for instruction-tuned models
    private static final List<Pattern> REFUSAL_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)i am sorry"),
            Pattern.compile("(?i)i apologize"),
            Pattern.compile("(?i)i cannot"),
            Pattern.compile("(?i)i am unable to"),
            Pattern.compile("(?i)as an ai language model"),
            Pattern.compile("(?i)as a language model"),
            Pattern.compile("(?i)i don't have enough information"),
            Pattern.compile("(?i)i do not have access")
    );

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
     * Now using a real Heuristic Hallucination & Refusal Detector!
     * 
     * @param response The response from the LLM model
     * @return true if reliable, false if escalation is needed
     */
    public boolean evaluateReliability(Map<String, Object> response) {
        String generation = (String) response.getOrDefault("generation", "");
        
        if (generation == null || generation.trim().isEmpty()) {
            return false; // Total failure
        }
        
        double confidenceScore = 1.0;
        
        // 1. Refusal Detection
        for (Pattern pattern : REFUSAL_PATTERNS) {
            if (pattern.matcher(generation).find()) {
                confidenceScore -= 0.5; // Huge penalty for refusals
                break;
            }
        }
        
        // 2. Loop / Repetition Detection (N-Gram / Token tracking proxy)
        // If the number of unique words is very low compared to total words, it's looping.
        List<String> words = Arrays.stream(generation.toLowerCase().split("\\W+"))
                .filter(w -> !w.isEmpty())
                .collect(Collectors.toList());
                
        if (words.size() > 50) {
            long uniqueWords = words.stream().distinct().count();
            double lexicalDiversity = (double) uniqueWords / words.size();
            
            // Typical conversational text has a lexical diversity > 0.4.
            // If it's less than 0.2, the model is stuck in a loop.
            if (lexicalDiversity < 0.2) {
                confidenceScore -= 0.3;
            } else if (lexicalDiversity < 0.3) {
                confidenceScore -= 0.15;
            }
        }
        
        // 3. Length Sanity Check
        if (words.size() < 3) {
            confidenceScore -= 0.2; // Too short to be a reliable answer to a complex prompt
        }
        
        // Add calculated score to the response for telemetry downstream
        response.put("confidence", Math.max(0.0, confidenceScore));
        
        return confidenceScore >= CONFIDENCE_THRESHOLD;
    }
}
