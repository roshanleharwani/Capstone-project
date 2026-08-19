package com.nexus.compression;

import com.nexus.compression.service.ProtectionFloorEnforcer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtectionFloorEnforcerTest {

    private ProtectionFloorEnforcer enforcer;

    @BeforeEach
    void setUp() {
        enforcer = new ProtectionFloorEnforcer();
    }

    @Test
    @DisplayName("Should extract NER pseudonym placeholders from text")
    void testExtractPlaceholders() {
        String text = "Contact {{PERSON_1}} at {{EMAIL_1}} regarding {{ORG_1}} contract.";
        List<String> phrases = enforcer.extractProtectedPhrases(text);

        assertTrue(phrases.contains("{{PERSON_1}}"));
        assertTrue(phrases.contains("{{EMAIL_1}}"));
        assertTrue(phrases.contains("{{ORG_1}}"));
        assertEquals(3, phrases.size());
    }

    @Test
    @DisplayName("Should extract RAG marker phrases from text")
    void testExtractRAGMarkers() {
        String text = "Based on [RAG: Q3 revenue was $4.2M] the forecast looks positive.";
        List<String> phrases = enforcer.extractProtectedPhrases(text);

        assertTrue(phrases.stream().anyMatch(p -> p.contains("RAG:")));
    }

    @Test
    @DisplayName("enforceFloors should re-inject dropped protected phrases")
    void testEnforceFloorsReInjectsDroppedPhrases() {
        String original = "Send report to {{EMAIL_1}} by Friday.";
        String compressed = "Send report Friday."; // {{EMAIL_1}} was dropped by compressor

        String enforced = enforcer.enforceFloors(original, compressed);

        assertTrue(enforced.contains("{{EMAIL_1}}"),
                "Dropped protected phrase must be re-injected");
    }

    @Test
    @DisplayName("enforceFloors should not duplicate phrases already present in compressed text")
    void testEnforceFloorsNoDuplication() {
        String original = "Contact {{PERSON_1}} for details.";
        String compressed = "Contact {{PERSON_1}} details."; // phrase survived

        String enforced = enforcer.enforceFloors(original, compressed);

        long count = countOccurrences(enforced, "{{PERSON_1}}");
        assertEquals(1, count, "Protected phrase should appear exactly once");
    }

    @Test
    @DisplayName("satisfiesFloors should return true when all protected phrases are present")
    void testSatisfiesFloorsTrue() {
        String original = "Call {{PHONE_1}} or email {{EMAIL_1}}.";
        String compressed = "Call {{PHONE_1}} email {{EMAIL_1}}.";

        assertTrue(enforcer.satisfiesFloors(original, compressed));
    }

    @Test
    @DisplayName("satisfiesFloors should return false when a protected phrase is missing")
    void testSatisfiesFloorsFalse() {
        String original = "Call {{PHONE_1}} or email {{EMAIL_1}}.";
        String compressed = "Call {{PHONE_1}}."; // EMAIL_1 dropped

        assertFalse(enforcer.satisfiesFloors(original, compressed));
    }

    @Test
    @DisplayName("Empty original text should return compressed text unchanged")
    void testEmptyOriginal() {
        String result = enforcer.enforceFloors("", "some compressed text");
        assertEquals("some compressed text", result);
    }

    private long countOccurrences(String text, String phrase) {
        long count = 0;
        int idx = 0;
        while ((idx = text.indexOf(phrase, idx)) != -1) {
            count++;
            idx += phrase.length();
        }
        return count;
    }
}
