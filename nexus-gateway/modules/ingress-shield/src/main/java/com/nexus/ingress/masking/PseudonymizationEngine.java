package com.nexus.ingress.masking;

import com.nexus.ingress.dto.ChatCompletionRequest;
import com.nexus.ingress.dto.ChatCompletionResponse;
import com.nexus.ingress.dto.ChatMessage;
import com.nexus.ingress.pii.PIIScanner;
import com.nexus.ingress.pii.RegexPIIScanner;
import com.nexus.ingress.pii.SecretScanner;
import com.nexus.ingress.pii.TransformerNERScanner;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3-Pass PII Scanning & Session-Stable Pseudonymization Engine (ISO/IEC 27001).
 * Features:
 * - 3-Pass Scanner Pipeline (Regex -> Secret -> Transformer NER)
 * - Do-Not-Mask allowlist preservation
 * - Session-stable type-preserving placeholder mapping (e.g., {{PERSON_1}}, {{EMAIL_1}})
 * - Data Classification & Restricted Tier-1 hard-route flagging
 * - Egress response de-pseudonymization rehydration
 */
@Component
public class PseudonymizationEngine {

    private final List<PIIScanner> scanners;
    private final DoNotMaskAllowlist allowlist;

    // Session ID -> (Raw Value -> Placeholder)
    private final Map<String, Map<String, String>> sessionPlaceholderMap = new ConcurrentHashMap<>();

    // Session ID -> (Placeholder -> Raw Value) for Egress Rehydration
    private final Map<String, Map<String, String>> sessionRehydrationMap = new ConcurrentHashMap<>();

    // Session ID -> Counter for entity types (e.g. PERSON -> 1, EMAIL -> 1)
    private final Map<String, Map<String, Integer>> sessionEntityCounters = new ConcurrentHashMap<>();

    public PseudonymizationEngine(RegexPIIScanner regexScanner,
                                  SecretScanner secretScanner,
                                  TransformerNERScanner nerScanner,
                                  DoNotMaskAllowlist allowlist) {
        this.scanners = List.of(regexScanner, secretScanner, nerScanner);
        this.allowlist = allowlist;
    }

    public ProcessedPayload sanitizeRequest(String sessionId, ChatCompletionRequest rawRequest) {
        if (rawRequest == null || rawRequest.getMessages() == null) {
            return new ProcessedPayload(rawRequest, Map.of(), DataClassification.PUBLIC, false);
        }

        String sid = (sessionId != null && !sessionId.isBlank()) ? sessionId : UUID.randomUUID().toString();
        Map<String, String> rawToPlaceholder = sessionPlaceholderMap.computeIfAbsent(sid, k -> new ConcurrentHashMap<>());
        Map<String, String> placeholderToRaw = sessionRehydrationMap.computeIfAbsent(sid, k -> new ConcurrentHashMap<>());
        Map<String, Integer> entityCounters = sessionEntityCounters.computeIfAbsent(sid, k -> new ConcurrentHashMap<>());

        boolean containsRestricted = false;
        boolean containsInternal = false;

        List<ChatMessage> sanitizedMessages = new ArrayList<>();

        for (ChatMessage message : rawRequest.getMessages()) {
            if (message.getContent() == null || message.getContent().isBlank()) {
                sanitizedMessages.add(message);
                continue;
            }

            String content = message.getContent();

            // Run 3-Pass Scanning Pipeline
            List<PIIScanner.PIIMatch> allMatches = new ArrayList<>();
            for (PIIScanner scanner : scanners) {
                allMatches.addAll(scanner.scan(content));
            }

            // Sort matches in reverse order of start position to replace cleanly without index shifting
            allMatches.sort(Comparator.comparingInt(PIIScanner.PIIMatch::getStart).reversed());

            StringBuilder sanitizedBuilder = new StringBuilder(content);

            for (PIIScanner.PIIMatch match : allMatches) {
                String originalText = match.getOriginalText();
                String entityType = match.getEntityType();

                // Skip if match is in Do-Not-Mask allowlist
                if (allowlist.shouldAllow(originalText)) {
                    continue;
                }

                // Classification rules
                if (entityType.contains("SECRET") || entityType.contains("KEY") || entityType.contains("RESTRICTED")) {
                    containsRestricted = true;
                } else {
                    containsInternal = true;
                }

                // Generate session-stable placeholder
                String placeholder = rawToPlaceholder.computeIfAbsent(originalText, raw -> {
                    int index = entityCounters.merge(entityType, 1, Integer::sum);
                    return "{{" + entityType + "_" + index + "}}";
                });

                placeholderToRaw.put(placeholder, originalText);

                // Replace in text snippet
                if (match.getStart() >= 0 && match.getEnd() <= sanitizedBuilder.length()
                        && sanitizedBuilder.substring(match.getStart(), match.getEnd()).equals(originalText)) {
                    sanitizedBuilder.replace(match.getStart(), match.getEnd(), placeholder);
                } else {
                    // Fallback literal replace
                    int idx = sanitizedBuilder.indexOf(originalText);
                    if (idx != -1) {
                        sanitizedBuilder.replace(idx, idx + originalText.length(), placeholder);
                    }
                }
            }

            ChatMessage sanitizedMsg = new ChatMessage(message.getRole(), sanitizedBuilder.toString(), message.getName());
            sanitizedMessages.add(sanitizedMsg);
        }

        ChatCompletionRequest sanitizedReq = new ChatCompletionRequest();
        sanitizedReq.setModel(rawRequest.getModel());
        sanitizedReq.setMessages(sanitizedMessages);
        sanitizedReq.setTemperature(rawRequest.getTemperature());
        sanitizedReq.setTopP(rawRequest.getTopP());
        sanitizedReq.setN(rawRequest.getN());
        sanitizedReq.setStream(rawRequest.getStream());
        sanitizedReq.setMaxTokens(rawRequest.getMaxTokens());
        sanitizedReq.setUser(rawRequest.getUser());
        sanitizedReq.setTenantId(rawRequest.getTenantId());

        DataClassification classification = DataClassification.PUBLIC;
        boolean hardRouteTier1 = false;

        if (containsRestricted) {
            classification = DataClassification.RESTRICTED;
            hardRouteTier1 = true; // RESTRICTED enterprise data HARD-ROUTED to Tier 1 Self-Hosted ONLY
        } else if (containsInternal) {
            classification = DataClassification.INTERNAL;
        }

        return new ProcessedPayload(sanitizedReq, new HashMap<>(rawToPlaceholder), classification, hardRouteTier1);
    }

    /**
     * Egress Rehydration: Replaces session placeholders back into raw values before returning to client.
     */
    public ChatCompletionResponse rehydrateResponse(String sessionId, ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || sessionId == null) {
            return response;
        }

        Map<String, String> placeholderToRaw = sessionRehydrationMap.get(sessionId);
        if (placeholderToRaw == null || placeholderToRaw.isEmpty()) {
            return response;
        }

        for (ChatCompletionResponse.Choice choice : response.getChoices()) {
            if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                String content = choice.getMessage().getContent();
                for (Map.Entry<String, String> entry : placeholderToRaw.entrySet()) {
                    content = content.replace(entry.getKey(), entry.getValue());
                }
                choice.getMessage().setContent(content);
            }
        }

        return response;
    }

    public static class ProcessedPayload {
        private final ChatCompletionRequest sanitizedRequest;
        private final Map<String, String> pseudonymMapping;
        private final DataClassification classification;
        private final boolean hardRouteTier1;

        public ProcessedPayload(ChatCompletionRequest sanitizedRequest,
                                Map<String, String> pseudonymMapping,
                                DataClassification classification,
                                boolean hardRouteTier1) {
            this.sanitizedRequest = sanitizedRequest;
            this.pseudonymMapping = pseudonymMapping;
            this.classification = classification;
            this.hardRouteTier1 = hardRouteTier1;
        }

        public ChatCompletionRequest getSanitizedRequest() {
            return sanitizedRequest;
        }

        public Map<String, String> getPseudonymMapping() {
            return pseudonymMapping;
        }

        public DataClassification getClassification() {
            return classification;
        }

        public boolean isHardRouteTier1() {
            return hardRouteTier1;
        }
    }
}
