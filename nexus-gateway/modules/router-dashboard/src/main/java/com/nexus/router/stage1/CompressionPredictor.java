package com.nexus.router.stage1;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CompressionPredictor {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your", "yours",
            "yourself", "yourselves", "he", "him", "his", "himself", "she", "her", "hers",
            "herself", "it", "its", "itself", "they", "them", "their", "theirs", "themselves",
            "what", "which", "who", "whom", "this", "that", "these", "those", "am", "is", "are",
            "was", "were", "be", "been", "being", "have", "has", "had", "having", "do", "does",
            "did", "doing", "a", "an", "the", "and", "but", "if", "or", "because", "as", "until",
            "while", "of", "at", "by", "for", "with", "about", "against", "between", "into",
            "through", "during", "before", "after", "above", "below", "to", "from", "up", "down",
            "in", "out", "on", "off", "over", "under", "again", "further", "then", "once", "here",
            "there", "when", "where", "why", "how", "all", "any", "both", "each", "few", "more",
            "most", "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so",
            "than", "too", "very", "s", "t", "can", "will", "just", "don", "should", "now"
    ));

    /**
     * Stage 1: Predicts the compression ratio (rho).
     * Analyzes the text using Feature-Based Statistical NLP (Information Density, Lexical Richness).
     * 
     * @param payload The request payload containing prompt length, task type, etc.
     * @return predicted compression ratio between 0.0 and 1.0
     */
    public double predictCompressionRatio(Map<String, Object> payload) {
        String prompt = (String) payload.getOrDefault("prompt", "");
        
        if (prompt.trim().isEmpty() || prompt.length() < 10) {
            return 0.0; // Too short to compress
        }

        // 1. Tokenize
        List<String> tokens = Arrays.stream(prompt.toLowerCase().split("\\W+"))
                                    .filter(t -> !t.isEmpty())
                                    .collect(Collectors.toList());
        
        if (tokens.isEmpty()) return 0.0;

        // 2. Calculate Linguistic Features
        double entropy = calculateShannonEntropy(tokens);
        double ttr = calculateTypeTokenRatio(tokens);
        double stopWordDensity = calculateStopWordDensity(tokens);

        // 3. Feature Integration (Weighted Linear Combination)
        // High Stop-Word Density -> Highly Compressible
        // Low TTR (repetitive) -> Highly Compressible
        // Low Entropy (predictable) -> Highly Compressible
        
        // Normalize entropy (usually between 0 and 6 for short/medium texts)
        double normalizedEntropy = Math.min(1.0, entropy / 6.0);
        
        double rawScore = (stopWordDensity * 0.4) + ((1.0 - ttr) * 0.3) + ((1.0 - normalizedEntropy) * 0.3);

        // Clamp between 0.05 and 0.85
        return Math.max(0.05, Math.min(0.85, rawScore));
    }

    private double calculateShannonEntropy(List<String> tokens) {
        Map<String, Long> frequencies = tokens.stream()
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
        
        double entropy = 0.0;
        int totalTokens = tokens.size();
        
        for (Long count : frequencies.values()) {
            double prob = (double) count / totalTokens;
            entropy -= prob * (Math.log(prob) / Math.log(2));
        }
        return entropy;
    }

    private double calculateTypeTokenRatio(List<String> tokens) {
        Set<String> uniqueTokens = new HashSet<>(tokens);
        return (double) uniqueTokens.size() / tokens.size();
    }

    private double calculateStopWordDensity(List<String> tokens) {
        long stopWordCount = tokens.stream()
                .filter(STOP_WORDS::contains)
                .count();
        return (double) stopWordCount / tokens.size();
    }
}
