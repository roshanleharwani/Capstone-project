package com.nexus.compression.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt Stratifier — separates the prompt into two layers:
 *
 *   Static Prefix  : system messages and tool/function schema definitions.
 *                    These are token-budget-fixed and must NEVER be compressed.
 *   Dynamic Payload: user/assistant conversation turns and retrieved RAG context.
 *                    These are the target for LLMLingua-2 compression.
 *
 * Stratification prevents the compressor from accidentally dropping critical
 * instruction framing or tool call signatures.
 */
@Component
public class PromptStratifier {

    public static class StratifiedPrompt {
        private final String staticPrefix;
        private final String dynamicPayload;
        private final int staticTokenEstimate;
        private final int dynamicTokenEstimate;

        public StratifiedPrompt(String staticPrefix, String dynamicPayload,
                                int staticTokenEstimate, int dynamicTokenEstimate) {
            this.staticPrefix = staticPrefix;
            this.dynamicPayload = dynamicPayload;
            this.staticTokenEstimate = staticTokenEstimate;
            this.dynamicTokenEstimate = dynamicTokenEstimate;
        }

        public String getStaticPrefix() { return staticPrefix; }
        public String getDynamicPayload() { return dynamicPayload; }
        public int getStaticTokenEstimate() { return staticTokenEstimate; }
        public int getDynamicTokenEstimate() { return dynamicTokenEstimate; }
        public int getTotalTokenEstimate() { return staticTokenEstimate + dynamicTokenEstimate; }
    }

    public static class Message {
        private final String role;
        private final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public String getContent() { return content; }
    }

    /**
     * Stratifies a list of messages into static prefix and dynamic payload.
     *
     * Rules:
     *   - role == "system"   → always static prefix
     *   - role == "tool"     → always static prefix (tool/function schemas)
     *   - role == "function" → always static prefix
     *   - role == "user" or "assistant" → dynamic payload
     */
    public StratifiedPrompt stratify(List<Message> messages) {
        List<String> staticParts = new ArrayList<>();
        List<String> dynamicParts = new ArrayList<>();

        for (Message msg : messages) {
            String formatted = formatMessage(msg);
            if (isStaticRole(msg.getRole())) {
                staticParts.add(formatted);
            } else {
                dynamicParts.add(formatted);
            }
        }

        String staticPrefix = String.join("\n", staticParts);
        String dynamicPayload = String.join("\n", dynamicParts);

        return new StratifiedPrompt(
                staticPrefix,
                dynamicPayload,
                estimateTokens(staticPrefix),
                estimateTokens(dynamicPayload)
        );
    }

    private boolean isStaticRole(String role) {
        if (role == null) return false;
        return role.equalsIgnoreCase("system")
                || role.equalsIgnoreCase("tool")
                || role.equalsIgnoreCase("function");
    }

    private String formatMessage(Message msg) {
        return "<|" + msg.getRole() + "|> " + msg.getContent();
    }

    /**
     * Approximate token count: ~4 characters per token (GPT-style BPE heuristic).
     */
    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }
}
