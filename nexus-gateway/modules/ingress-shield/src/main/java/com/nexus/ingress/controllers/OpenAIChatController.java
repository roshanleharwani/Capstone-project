package com.nexus.ingress.controllers;

import com.nexus.ingress.dto.ChatCompletionRequest;
import com.nexus.ingress.dto.ChatCompletionResponse;
import com.nexus.ingress.dto.ChatMessage;
import com.nexus.ingress.dto.SanitizedContextMock;
import com.nexus.ingress.dto.ShieldScanResult;
import com.nexus.ingress.masking.PseudonymizationEngine;
import com.nexus.ingress.shields.PromptInjectionShield;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Primary REST Controller exposing OpenAI API Specification endpoint (/v1/chat/completions).
 * Implements ZTEP Ingress Shield Pipeline:
 * Auth & mTLS Verification -> Ingress Injection Shield -> 3-Pass PII Pseudonymization ->
 * Data Classification & Restricted Hard-Routing Flag -> Pipeline Execution Bridge -> Egress Rehydration.
 */
@RestController
@RequestMapping("/v1")
public class OpenAIChatController {

    private final PromptInjectionShield promptInjectionShield;
    private final PseudonymizationEngine pseudonymizationEngine;

    public OpenAIChatController(PromptInjectionShield promptInjectionShield,
                                PseudonymizationEngine pseudonymizationEngine) {
        this.promptInjectionShield = promptInjectionShield;
        this.pseudonymizationEngine = pseudonymizationEngine;
    }

    @PostMapping("/chat/completions")
    public ResponseEntity<?> createChatCompletion(
            @RequestBody ChatCompletionRequest request,
            @RequestHeader(value = "X-Session-ID", required = false) String headerSessionId,
            HttpServletRequest httpServletRequest) {

        String sessionId = (headerSessionId != null && !headerSessionId.isBlank())
                ? headerSessionId
                : UUID.randomUUID().toString();

        String tenantId = (String) httpServletRequest.getAttribute("X-Tenant-ID");
        if (tenantId == null) tenantId = "default-tenant";

        // 1. Network-Layer Prompt Injection Shield Inspection (OWASP LLM01)
        ShieldScanResult shieldResult = promptInjectionShield.inspectMessages(request.getMessages());
        if (!shieldResult.isSafe()) {
            Map<String, Object> errorBody = Map.of(
                    "error", Map.of(
                            "message", "Security Policy Violation: " + shieldResult.getReason(),
                            "type", "prompt_injection_detected",
                            "code", shieldResult.getCategory().toLowerCase(),
                            "risk_score", shieldResult.getRiskScore()
                    )
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
        }

        // 2. 3-Pass PII Scanning & Session-Stable Pseudonymization
        PseudonymizationEngine.ProcessedPayload processedPayload =
                pseudonymizationEngine.sanitizeRequest(sessionId, request);

        // 3. Construct Sanitized Context Bridge for Downstream Pipeline
        SanitizedContextMock sanitizedContext = new SanitizedContextMock(
                sessionId,
                tenantId,
                processedPayload.getSanitizedRequest(),
                processedPayload.getPseudonymMapping(),
                processedPayload.getClassification(),
                processedPayload.isHardRouteTier1(),
                Map.of(
                        "authType", "DUAL_JWT_APIKEY",
                        "mtlsVerified", "true",
                        "hardRouteTier1", String.valueOf(processedPayload.isHardRouteTier1())
                )
        );

        // 4. Mock Pipeline & LLM Backend Execution
        ChatCompletionResponse mockRawResponse = generateMockModelResponse(
                processedPayload.getSanitizedRequest(),
                processedPayload.isHardRouteTier1()
        );

        // 5. Egress De-pseudonymization Rehydration
        ChatCompletionResponse rehydratedResponse =
                pseudonymizationEngine.rehydrateResponse(sessionId, mockRawResponse);

        return ResponseEntity.ok()
                .header("X-Session-ID", sessionId)
                .header("X-Data-Classification", processedPayload.getClassification().name())
                .header("X-Hard-Route-Tier1", String.valueOf(processedPayload.isHardRouteTier1()))
                .body(rehydratedResponse);
    }

    private ChatCompletionResponse generateMockModelResponse(ChatCompletionRequest request, boolean isTier1HardRouted) {
        String lastUserMessage = "";
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            ChatMessage lastMsg = request.getMessages().get(request.getMessages().size() - 1);
            lastUserMessage = lastMsg.getContent();
        }

        String targetModel = isTier1HardRouted ? "vllm-tier1-llama3-70b-restricted" : (request.getModel() != null ? request.getModel() : "gpt-4o");
        String choiceContent = "Processed prompt: " + lastUserMessage + " [Executed via " + targetModel + "]";

        ChatMessage assistantMsg = new ChatMessage("assistant", choiceContent);
        ChatCompletionResponse.Choice choice = new ChatCompletionResponse.Choice(0, assistantMsg, "stop");
        ChatCompletionResponse.Usage usage = new ChatCompletionResponse.Usage(15, 25, 40);

        return new ChatCompletionResponse(
                "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8),
                targetModel,
                List.of(choice),
                usage
        );
    }
}
