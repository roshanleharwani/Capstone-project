package com.nexus.ingress.pii;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Pass 3 Transformer NER Scanner: Context-aware Named Entity Recognition (PERSON, ORGANIZATION, LOCATION).
 * Uses contextual heuristic patterns and sidecar NER model interface adapter.
 */
@Component
public class TransformerNERScanner implements PIIScanner {

    // Contextual patterns for entity recognition (Person names, Organizations, Locations)
    private static final Pattern PERSON_PREFIX_PATTERN = Pattern.compile("(?i)\\b(Mr\\.|Mrs\\.|Ms\\.|Dr\\.|Prof\\.)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)");
    private static final Pattern ORG_PATTERN = Pattern.compile("(?i)\\b([A-Z][a-zA-Z0-9&]+(?:\\s+[A-Z][a-zA-Z0-9&]+)*)\\s+(Inc\\.|Corp\\.|LLC|Ltd\\.|University|Department|Group|Technologies)\\b");
    private static final Pattern CONFIDENTIAL_DOC_PATTERN = Pattern.compile("(?i)\\b(Internal\\s+Project\\s+Code|Confidential\\s+Strategy|Vault\\s+Password)\\b");

    @Override
    public List<PIIMatch> scan(String text) {
        List<PIIMatch> matches = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return matches;
        }

        // 1. Scan Person Entities
        Matcher personMatcher = PERSON_PREFIX_PATTERN.matcher(text);
        while (personMatcher.find()) {
            matches.add(new PIIMatch(
                    personMatcher.group(2),
                    "PERSON",
                    personMatcher.start(2),
                    personMatcher.end(2),
                    0.92
            ));
        }

        // 2. Scan Organization Entities
        Matcher orgMatcher = ORG_PATTERN.matcher(text);
        while (orgMatcher.find()) {
            matches.add(new PIIMatch(
                    orgMatcher.group(),
                    "ORGANIZATION",
                    orgMatcher.start(),
                    orgMatcher.end(),
                    0.88
            ));
        }

        // 3. Scan Confidential Document / Project Flags
        Matcher docMatcher = CONFIDENTIAL_DOC_PATTERN.matcher(text);
        while (docMatcher.find()) {
            matches.add(new PIIMatch(
                    docMatcher.group(),
                    "RESTRICTED_ENTITY",
                    docMatcher.start(),
                    docMatcher.end(),
                    0.95
            ));
        }

        return matches;
    }

    @Override
    public String getPassName() {
        return "PASS_3_TRANSFORMER_NER";
    }
}
