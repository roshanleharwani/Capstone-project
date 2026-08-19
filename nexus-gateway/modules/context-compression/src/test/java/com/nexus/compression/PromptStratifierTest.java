package com.nexus.compression;

import com.nexus.compression.service.PromptStratifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptStratifierTest {

    private PromptStratifier stratifier;

    @BeforeEach
    void setUp() {
        stratifier = new PromptStratifier();
    }

    @Test
    @DisplayName("System messages should be placed in static prefix")
    void testSystemMessageGoesToStaticPrefix() {
        List<PromptStratifier.Message> messages = List.of(
                new PromptStratifier.Message("system", "You are a helpful assistant."),
                new PromptStratifier.Message("user", "What is the weather?")
        );

        PromptStratifier.StratifiedPrompt result = stratifier.stratify(messages);

        assertTrue(result.getStaticPrefix().contains("You are a helpful assistant."));
        assertFalse(result.getDynamicPayload().contains("You are a helpful assistant."));
    }

    @Test
    @DisplayName("User and assistant messages should be placed in dynamic payload")
    void testUserAssistantGoesToDynamicPayload() {
        List<PromptStratifier.Message> messages = List.of(
                new PromptStratifier.Message("user", "Hello"),
                new PromptStratifier.Message("assistant", "Hi there!")
        );

        PromptStratifier.StratifiedPrompt result = stratifier.stratify(messages);

        assertTrue(result.getDynamicPayload().contains("Hello"));
        assertTrue(result.getDynamicPayload().contains("Hi there!"));
        assertTrue(result.getStaticPrefix().isBlank());
    }

    @Test
    @DisplayName("Tool messages should be placed in static prefix")
    void testToolMessageGoesToStaticPrefix() {
        List<PromptStratifier.Message> messages = List.of(
                new PromptStratifier.Message("tool", "{\"name\":\"get_weather\",\"parameters\":{}}"),
                new PromptStratifier.Message("user", "What is the weather in Paris?")
        );

        PromptStratifier.StratifiedPrompt result = stratifier.stratify(messages);

        assertTrue(result.getStaticPrefix().contains("get_weather"));
        assertFalse(result.getDynamicPayload().contains("get_weather"));
    }

    @Test
    @DisplayName("Token estimates should be positive for non-empty text")
    void testTokenEstimation() {
        List<PromptStratifier.Message> messages = List.of(
                new PromptStratifier.Message("system", "You are an AI."),
                new PromptStratifier.Message("user", "Tell me about quantum computing in detail.")
        );

        PromptStratifier.StratifiedPrompt result = stratifier.stratify(messages);

        assertTrue(result.getStaticTokenEstimate() > 0);
        assertTrue(result.getDynamicTokenEstimate() > 0);
        assertEquals(result.getStaticTokenEstimate() + result.getDynamicTokenEstimate(),
                result.getTotalTokenEstimate());
    }

    @Test
    @DisplayName("Empty message list should produce empty prefix and payload")
    void testEmptyMessages() {
        PromptStratifier.StratifiedPrompt result = stratifier.stratify(List.of());

        assertTrue(result.getStaticPrefix().isBlank());
        assertTrue(result.getDynamicPayload().isBlank());
        assertEquals(0, result.getTotalTokenEstimate());
    }
}
