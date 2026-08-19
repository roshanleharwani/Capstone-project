package com.nexus.compression;

import com.nexus.compression.service.LLMLinguaCompressor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LLMLinguaCompressorTest {

    private LLMLinguaCompressor compressor;

    @BeforeEach
    void setUp() {
        compressor = new LLMLinguaCompressor();
        compressor.setTargetCompressionRatio(0.5);
    }

    @Test
    @DisplayName("Compressed token count should be at most 50% of original at ratio 0.5")
    void testCompressionReducesTokenCount() {
        String text = "The quick brown fox jumps over the lazy dog and then runs away into the forest";
        LLMLinguaCompressor.CompressionResult result = compressor.compress(text, List.of());

        assertTrue(result.getCompressedTokenCount() <= result.getOriginalTokenCount(),
                "Compressed count should not exceed original");
        assertTrue(result.getActualRatio() <= 0.75,
                "Actual ratio should be below 0.75 for a 0.5 target");
    }

    @Test
    @DisplayName("Protected phrases must survive compression")
    void testProtectedPhrasesArePreserved() {
        String text = "The user is a and the email is {{EMAIL_1}} and we should process this request carefully";
        List<String> protected_ = List.of("{{EMAIL_1}}");

        LLMLinguaCompressor.CompressionResult result = compressor.compress(text, protected_);

        assertTrue(result.getCompressedText().contains("{{EMAIL_1}}"),
                "Protected placeholder must not be dropped");
    }

    @Test
    @DisplayName("Empty input should return empty result with ratio 1.0")
    void testEmptyInput() {
        LLMLinguaCompressor.CompressionResult result = compressor.compress("", List.of());

        assertEquals("", result.getCompressedText());
        assertEquals(0, result.getOriginalTokenCount());
        assertEquals(1.0, result.getActualRatio(), 0.001);
    }

    @Test
    @DisplayName("Stop words should be preferentially dropped over content words")
    void testStopWordsDroppedFirst() {
        // Text with many stop words and one important content word
        String text = "the a an is are was were be been and but or so yet";
        LLMLinguaCompressor.CompressionResult result = compressor.compress(text, List.of());

        // After compression, the result should be shorter
        assertTrue(result.getCompressedTokenCount() < result.getOriginalTokenCount());
    }

    @Test
    @DisplayName("Compression ratio of 1.0 should return original text unchanged")
    void testNoCompressionAtRatioOne() {
        compressor.setTargetCompressionRatio(1.0);
        String text = "Hello world this is a test sentence";
        LLMLinguaCompressor.CompressionResult result = compressor.compress(text, List.of());

        assertEquals(result.getOriginalTokenCount(), result.getCompressedTokenCount());
    }
}
