package com.nexus.compression.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Protection Floor Enforcer.
 *
 * Guarantees that the following phrase categories are NEVER removed by the compressor:
 *   1. NER-flagged entities  — pseudonym placeholders like {{PERSON_1}}, {{EMAIL_1}}, {{ORG_1}}
 *   2. RAG-matched phrases   — verbatim chunks retrieved from the vector store (marked with [RAG:...])
 *   3. Explicit protection markers — phrases wrapped in [PROTECT:...] by upstream modules
 *
 * The enforcer works in two passes:
 *   Pre-compression  : extract all protected spans and their positions.
 *   Post-compression : verify all protected phrases are still present; re-inject any that were dropped.
 */
@Component
public class ProtectionFloorEnforcer {

    // Matches pseudonym placeholders: {{TYPE_N}}
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{[A-Z_]+_\\d+\\}\\}");

    // Matches RAG-retrieved chunks: [RAG: ... ]
    private static final Pattern RAG_MARKER_PATTERN = Pattern.compile("\\[RAG:[^\\]]+\\]");

    // Matches explicit protection markers: [PROTECT: ... ]
    private static final Pattern PROTECT_MARKER_PATTERN = Pattern.compile("\\[PROTECT:[^\\]]+\\]");

    /**
     * Extract all phrases that must be protected from compression.
     */
    public List<String> extractProtectedPhrases(String text) {
        List<String> protected_ = new ArrayList<>();
        if (text == null || text.isBlank()) return protected_;

        addMatches(PLACEHOLDER_PATTERN, text, protected_);
        addMatches(RAG_MARKER_PATTERN, text, protected_);
        addMatches(PROTECT_MARKER_PATTERN, text, protected_);

        return protected_;
    }

    /**
     * Post-compression enforcement: verify all protected phrases survived.
     * Any dropped phrase is appended back to the compressed text.
     *
     * @param original   original dynamic payload (pre-compression)
     * @param compressed output from LLMLinguaCompressor
     * @return enforced text with all protected phrases guaranteed present
     */
    public String enforceFloors(String original, String compressed) {
        if (original == null || original.isBlank()) return compressed;
        if (compressed == null) compressed = "";

        List<String> required = extractProtectedPhrases(original);
        StringBuilder result = new StringBuilder(compressed);

        for (String phrase : required) {
            if (!result.toString().contains(phrase)) {
                result.append(" ").append(phrase);
            }
        }

        return result.toString().trim();
    }

    /**
     * Checks whether the compressed text satisfies all protection floors.
     */
    public boolean satisfiesFloors(String original, String compressed) {
        List<String> required = extractProtectedPhrases(original);
        if (required.isEmpty()) return true;
        return required.stream().allMatch(phrase -> compressed != null && compressed.contains(phrase));
    }

    private void addMatches(Pattern pattern, String text, List<String> out) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String match = m.group();
            if (!out.contains(match)) {
                out.add(match);
            }
        }
    }
}
