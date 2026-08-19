package com.nexus.compression.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * LLMLingua-2 Single-Pass Token Compressor.
 *
 * In production this component calls the LLMLingua-2 sidecar microservice (Python/FastAPI).
 * This implementation provides a deterministic, rule-based approximation suitable for
 * unit testing and integration without the sidecar:
 *
 *   1. Tokenise by whitespace.
 *   2. Score each token by an importance heuristic (length, position, stop-word check).
 *   3. Drop the lowest-scoring tokens until the target compression ratio is met.
 *   4. Reconstruct the compressed string.
 *
 * Protected phrases (from ProtectionFloorEnforcer) are pinned and never dropped.
 */
@Component
public class LLMLinguaCompressor {

    @Value("${compression.target-ratio:0.5}")
    private double targetCompressionRatio;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "can", "to", "of", "in", "on",
            "at", "by", "for", "with", "about", "as", "into", "through", "and",
            "but", "or", "nor", "so", "yet", "both", "either", "neither", "not",
            "also", "just", "very", "too", "quite", "rather", "really", "that",
            "this", "these", "those", "it", "its", "i", "you", "he", "she", "we",
            "they", "me", "him", "her", "us", "them", "my", "your", "his", "our"
    );

    public static class CompressionResult {
        private final String compressedText;
        private final int originalTokenCount;
        private final int compressedTokenCount;
        private final double actualRatio;

        public CompressionResult(String compressedText, int originalTokenCount,
                                 int compressedTokenCount, double actualRatio) {
            this.compressedText = compressedText;
            this.originalTokenCount = originalTokenCount;
            this.compressedTokenCount = compressedTokenCount;
            this.actualRatio = actualRatio;
        }

        public String getCompressedText() { return compressedText; }
        public int getOriginalTokenCount() { return originalTokenCount; }
        public int getCompressedTokenCount() { return compressedTokenCount; }
        public double getActualRatio() { return actualRatio; }
    }

    /**
     * Compress the dynamic payload in a single pass.
     *
     * @param dynamicPayload   text to compress
     * @param protectedPhrases phrases that must not be dropped
     * @return CompressionResult with compressed text and token statistics
     */
    public CompressionResult compress(String dynamicPayload, List<String> protectedPhrases) {
        if (dynamicPayload == null || dynamicPayload.isBlank()) {
            return new CompressionResult("", 0, 0, 1.0);
        }

        String[] tokens = dynamicPayload.split("(?<=\\s)|(?=\\s)");
        int originalCount = countNonWhitespaceTokens(tokens);
        int targetCount = Math.max(1, (int) Math.ceil(originalCount * targetCompressionRatio));

        // Score each token; protected tokens get max score
        double[] scores = scoreTokens(tokens, dynamicPayload, protectedPhrases);

        // Build sorted drop list (lowest score first), skipping whitespace and protected tokens
        List<Integer> droppable = new ArrayList<>();
        for (int i = 0; i < tokens.length; i++) {
            if (!tokens[i].isBlank() && scores[i] < Double.MAX_VALUE) {
                droppable.add(i);
            }
        }
        droppable.sort(Comparator.comparingDouble(i -> scores[i]));

        // Drop tokens until we reach target count
        Set<Integer> dropped = new HashSet<>();
        int currentCount = originalCount;
        for (int idx : droppable) {
            if (currentCount <= targetCount) break;
            dropped.add(idx);
            currentCount--;
        }

        // Reconstruct
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (!dropped.contains(i)) {
                sb.append(tokens[i]);
            }
        }

        String compressed = sb.toString().replaceAll("\\s{2,}", " ").trim();
        int compressedCount = countNonWhitespaceTokens(compressed.split("(?<=\\s)|(?=\\s)"));
        double actualRatio = originalCount == 0 ? 1.0 : (double) compressedCount / originalCount;

        return new CompressionResult(compressed, originalCount, compressedCount, actualRatio);
    }

    private double[] scoreTokens(String[] tokens, String fullText, List<String> protectedPhrases) {
        double[] scores = new double[tokens.length];

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();

            if (token.isEmpty()) {
                scores[i] = Double.MAX_VALUE; // never drop whitespace tokens
                continue;
            }

            // Pin protected phrases
            if (isProtected(token, fullText, protectedPhrases)) {
                scores[i] = Double.MAX_VALUE;
                continue;
            }

            double score = 0.0;

            // Stop words are low-value
            if (STOP_WORDS.contains(token.toLowerCase())) {
                score -= 2.0;
            }

            // Longer tokens carry more information
            score += Math.min(token.length() / 5.0, 2.0);

            // Tokens near the end of the context are more relevant (recency bias)
            score += (double) i / tokens.length;

            // Capitalised tokens (likely named entities) are more important
            if (Character.isUpperCase(token.charAt(0))) {
                score += 1.5;
            }

            // Numeric tokens (dates, IDs) are important
            if (token.matches(".*\\d.*")) {
                score += 1.0;
            }

            scores[i] = score;
        }
        return scores;
    }

    private boolean isProtected(String token, String fullText, List<String> protectedPhrases) {
        if (protectedPhrases == null) return false;
        for (String phrase : protectedPhrases) {
            if (phrase.contains(token) || token.contains(phrase)) return true;
        }
        return false;
    }

    private int countNonWhitespaceTokens(String[] tokens) {
        int count = 0;
        for (String t : tokens) {
            if (!t.isBlank()) count++;
        }
        return count;
    }

    public void setTargetCompressionRatio(double ratio) {
        this.targetCompressionRatio = ratio;
    }

    public double getTargetCompressionRatio() {
        return targetCompressionRatio;
    }
}
