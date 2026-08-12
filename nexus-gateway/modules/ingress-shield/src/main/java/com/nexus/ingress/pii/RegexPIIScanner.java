package com.nexus.ingress.pii;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pass 1 PII Scanner: Fast, high-performance regex detection for SSNs, credit cards, emails, phone numbers, and IP addresses.
 */
@Component
public class RegexPIIScanner implements PIIScanner {

    private static final Map<String, Pattern> PATTERNS = Map.of(
            "EMAIL", Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}"),
            "SSN", Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
            "CREDIT_CARD", Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b"),
            "PHONE", Pattern.compile("\\b(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b"),
            "IP_ADDRESS", Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b")
    );

    @Override
    public List<PIIMatch> scan(String text) {
        List<PIIMatch> matches = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return matches;
        }

        for (Map.Entry<String, Pattern> entry : PATTERNS.entrySet()) {
            String entityType = entry.getKey();
            Matcher matcher = entry.getValue().matcher(text);

            while (matcher.find()) {
                matches.add(new PIIMatch(
                        matcher.group(),
                        entityType,
                        matcher.start(),
                        matcher.end(),
                        1.0
                ));
            }
        }
        return matches;
    }

    @Override
    public String getPassName() {
        return "PASS_1_REGEX";
    }
}
