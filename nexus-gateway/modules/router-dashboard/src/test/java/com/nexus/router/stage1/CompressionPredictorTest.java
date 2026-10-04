package com.nexus.router.stage1;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressionPredictorTest {

    private final CompressionPredictor predictor = new CompressionPredictor();

    @Test
    void testPredictCompressionRatio_ShortPrompt() {
        double ratio = predictor.predictCompressionRatio(Map.of("prompt", "short"));
        assertEquals(0.0, ratio, 0.01);
    }

    @Test
    void testPredictCompressionRatio_HighRepetitionAndStopWords() {
        // A prompt with high stop-word density and high repetition (low TTR, low entropy) -> highly compressible
        String prompt = "the the the the the the the the the the and and and and and and and and";
        double ratio = predictor.predictCompressionRatio(Map.of("prompt", prompt));
        // Should be near the max clamp (0.85) or at least highly compressible (> 0.6)
        assertTrue(ratio > 0.6);
    }

    @Test
    void testPredictCompressionRatio_DenseTechnicalText() {
        // A prompt with almost no stop words, high TTR (many unique tokens) -> low compressibility
        String prompt = "Calculate cryptographic SHA256 checksum polynomial vector algorithm matrix transformation initialization";
        double ratio = predictor.predictCompressionRatio(Map.of("prompt", prompt));
        // Should be near the min clamp (0.05) or at least not highly compressible (< 0.3)
        assertTrue(ratio < 0.3);
    }
}
