package com.nexus.ingress.masking;

import com.nexus.ingress.dto.ChatCompletionRequest;
import com.nexus.ingress.dto.ChatCompletionResponse;
import com.nexus.ingress.dto.ChatMessage;
import com.nexus.ingress.pii.RegexPIIScanner;
import com.nexus.ingress.pii.SecretScanner;
import com.nexus.ingress.pii.TransformerNERScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PseudonymizationEngineTest {

    private PseudonymizationEngine pseudonymizationEngine;

    @BeforeEach
    void setUp() {
        RegexPIIScanner regexScanner = new RegexPIIScanner();
        SecretScanner secretScanner = new SecretScanner();
        TransformerNERScanner nerScanner = new TransformerNERScanner();
        DoNotMaskAllowlist allowlist = new DoNotMaskAllowlist();

        pseudonymizationEngine = new PseudonymizationEngine(regexScanner, secretScanner, nerScanner, allowlist);
    }

    @Test
    @DisplayName("Should detect email PII and replace with session-stable placeholder")
    void testEmailPIIMasking() {
        String sessionId = "test-session-1";
        ChatMessage msg = new ChatMessage("user", "Hello, my email address is john.doe@example.com.");
        ChatCompletionRequest req = new ChatCompletionRequest("gpt-4o", List.of(msg));

        PseudonymizationEngine.ProcessedPayload payload = pseudonymizationEngine.sanitizeRequest(sessionId, req);

        assertNotNull(payload);
        assertEquals(DataClassification.INTERNAL, payload.getClassification());
        assertFalse(payload.isHardRouteTier1());

        String sanitizedContent = payload.getSanitizedRequest().getMessages().get(0).getContent();
        assertTrue(sanitizedContent.contains("{{EMAIL_1}}"));
        assertFalse(sanitizedContent.contains("john.doe@example.com"));

        // Egress Rehydration Test
        ChatMessage assistantMsg = new ChatMessage("assistant", "Sent verification link to {{EMAIL_1}}.");
        ChatCompletionResponse response = new ChatCompletionResponse("resp-1", "gpt-4o",
                List.of(new ChatCompletionResponse.Choice(0, assistantMsg, "stop")),
                new ChatCompletionResponse.Usage(10, 10, 20));

        ChatCompletionResponse rehydrated = pseudonymizationEngine.rehydrateResponse(sessionId, response);
        String rehydratedContent = rehydrated.getChoices().get(0).getMessage().getContent();
        assertTrue(rehydratedContent.contains("john.doe@example.com"));
        assertFalse(rehydratedContent.contains("{{EMAIL_1}}"));
    }

    @Test
    @DisplayName("Should preserve allowlist terms like 'United States' and 'OpenAI'")
    void testDoNotMaskAllowlist() {
        String sessionId = "test-session-2";
        ChatMessage msg = new ChatMessage("user", "Dr. Alice lives in the United States and works with OpenAI.");
        ChatCompletionRequest req = new ChatCompletionRequest("gpt-4o", List.of(msg));

        PseudonymizationEngine.ProcessedPayload payload = pseudonymizationEngine.sanitizeRequest(sessionId, req);

        String sanitizedContent = payload.getSanitizedRequest().getMessages().get(0).getContent();
        assertTrue(sanitizedContent.contains("United States"));
        assertTrue(sanitizedContent.contains("OpenAI"));
    }

    @Test
    @DisplayName("Should classify secret API key as RESTRICTED and flag for Tier 1 hard-routing")
    void testSecretKeyRestrictedHardRoute() {
        String sessionId = "test-session-3";
        ChatMessage msg = new ChatMessage("user", "My AWS Access Key is AKIA1234567890ABCDEF.");
        ChatCompletionRequest req = new ChatCompletionRequest("gpt-4o", List.of(msg));

        PseudonymizationEngine.ProcessedPayload payload = pseudonymizationEngine.sanitizeRequest(sessionId, req);

        assertEquals(DataClassification.RESTRICTED, payload.getClassification());
        assertTrue(payload.isHardRouteTier1(), "RESTRICTED payload must be hard-routed to Tier 1 Self-Hosted LLM");
    }

    @Test
    @DisplayName("Should maintain session-stable token consistency across multiple turns")
    void testSessionStableConsistency() {
        String sessionId = "multi-turn-session";
        ChatMessage msg1 = new ChatMessage("user", "My email is alice@example.com.");
        ChatMessage msg2 = new ChatMessage("user", "Can you send the report to alice@example.com again?");

        ChatCompletionRequest req1 = new ChatCompletionRequest("gpt-4o", List.of(msg1));
        ChatCompletionRequest req2 = new ChatCompletionRequest("gpt-4o", List.of(msg2));

        PseudonymizationEngine.ProcessedPayload p1 = pseudonymizationEngine.sanitizeRequest(sessionId, req1);
        PseudonymizationEngine.ProcessedPayload p2 = pseudonymizationEngine.sanitizeRequest(sessionId, req2);

        String placeholder1 = p1.getPseudonymMapping().get("alice@example.com");
        String placeholder2 = p2.getPseudonymMapping().get("alice@example.com");

        assertNotNull(placeholder1);
        assertEquals(placeholder1, placeholder2, "Same PII in same session must map to identical pseudonym placeholder");
    }
}
