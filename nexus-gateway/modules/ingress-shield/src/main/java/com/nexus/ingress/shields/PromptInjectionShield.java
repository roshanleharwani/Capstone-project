package com.nexus.ingress.shields;

import com.nexus.ingress.dto.ChatMessage;
import com.nexus.ingress.dto.ShieldScanResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Network-layer Prompt Injection & Jailbreak Shield (OWASP LLM01 - Direct/Indirect Prompt Injection).
 * Inspects incoming prompts for jailbreak signatures, system prompt override attempts, and delimiter tampering.
 */
@Component
public class PromptInjectionShield {

    // Regex patterns for common jailbreak attacks, DAN mode, system prompt overrides, and delimiter escaping
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+(instructions|prompts|rules)"),
            Pattern.compile("(?i)disregard\\s+all\\s+prior\\s+context"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(in\\s+)?DAN\\s+mode"),
            Pattern.compile("(?i)do\\s+anything\\s+now"),
            Pattern.compile("(?i)act\\s+as\\s+an?\\s+unfiltered\\s+AI"),
            Pattern.compile("(?i)jailbreak\\s+mode\\s+enabled"),
            Pattern.compile("(?i)<\\|im_start\\|>system"),
            Pattern.compile("(?i)```system\\s+override"),
            Pattern.compile("(?i)\\[SYSTEM\\s+NOTE:\\s*override\\b"),
            Pattern.compile("(?i)bypass\\s+safety\\s+filters?")
    );

    /**
     * Inspects list of messages for prompt injections.
     */
    public ShieldScanResult inspectMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return ShieldScanResult.safe();
        }

        for (ChatMessage message : messages) {
            String content = message.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }

            ShieldScanResult result = inspectText(content);
            if (!result.isSafe()) {
                return result;
            }
        }

        return ShieldScanResult.safe();
    }

    /**
     * Evaluates text string for prompt injection violations.
     */
    public ShieldScanResult inspectText(String text) {
        if (text == null || text.isBlank()) {
            return ShieldScanResult.safe();
        }

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return ShieldScanResult.violation(
                        "PROMPT_INJECTION_DIRECT",
                        0.95,
                        "Direct prompt injection or jailbreak pattern detected matching rule: " + pattern.pattern()
                );
            }
        }

        // Additional heuristic checks (e.g. excessive delimiter manipulation)
        if (text.contains("<|im_start|>") || text.contains("<|im_end|>") || text.contains("```system")) {
            return ShieldScanResult.violation(
                    "DELIMITER_TAMPERING",
                    0.85,
                    "Prompt contains illegal systemic system context delimiters"
            );
        }

        return ShieldScanResult.safe();
    }
}
