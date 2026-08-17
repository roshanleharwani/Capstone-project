package com.nexus.router.stage1;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompressionPredictorTest {

    private final CompressionPredictor predictor = new CompressionPredictor();

    @Test
    void testPredictCompressionRatio_ShortPrompt() {
        double ratio = predictor.predictCompressionRatio(Map.of("prompt", "short query"));
        assertEquals(0.0, ratio, 0.01);
    }

    @Test
    void testPredictCompressionRatio_MathOrCode() {
        double ratio = predictor.predictCompressionRatio(Map.of("prompt", "This is a long query containing some code snippets."));
        assertEquals(0.2, ratio, 0.01);
    }

    @Test
    void testPredictCompressionRatio_Default() {
        double ratio = predictor.predictCompressionRatio(Map.of("prompt", "This is a very long text query without any special keywords that might otherwise prevent compression. It should get the default moderate ratio."));
        assertEquals(0.5, ratio, 0.01);
    }
}
