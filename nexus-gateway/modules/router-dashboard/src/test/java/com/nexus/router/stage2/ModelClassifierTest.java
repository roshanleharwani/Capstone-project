package com.nexus.router.stage2;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelClassifierTest {

    private final ModelClassifier classifier = new ModelClassifier();

    @Test
    void testClassify_RestrictedData() {
        String tier = classifier.classifyModelTier(Map.of("dataClassification", "RESTRICTED"), 0.9);
        assertEquals("TIER1", tier);
    }

    @Test
    void testClassify_HighCompression() {
        String tier = classifier.classifyModelTier(Map.of("dataClassification", "PUBLIC"), 0.5);
        assertEquals("TIER2", tier);
    }

    @Test
    void testClassify_LowCompression() {
        String tier = classifier.classifyModelTier(Map.of("dataClassification", "INTERNAL"), 0.2);
        assertEquals("TIER1", tier);
    }
}
