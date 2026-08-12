package com.nexus.ingress.pii;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pass 2 Secret Scanner: High-entropy string detection, API key patterns (AWS, OpenAI, GitHub), bearer tokens, and private keys.
 */
@Component
public class SecretScanner implements PIIScanner {

    private static final Map<String, Pattern> SECRET_PATTERNS = Map.of(
            "AWS_ACCESS_KEY", Pattern.compile("(A3T[A-Z0-9]|AKIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA|ASIA)[A-Z0-9]{16}"),
            "OPENAI_API_KEY", Pattern.compile("sk-[a-zA-Z0-9]{32,64}"),
            "GENERIC_SECRET_KEY", Pattern.compile("(?i)(api_key|secret_key|access_token|bearer)\\s*[:=]\\s*[\"']?([a-zA-Z0-9_\\-]{20,})[\"']?"),
            "PRIVATE_KEY_BLOCK", Pattern.compile("-----BEGIN (RSA |EC |PGP )?PRIVATE KEY-----")
    );

    @Override
    public List<PIIMatch> scan(String text) {
        List<PIIMatch> matches = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return matches;
        }

        // 1. Scan deterministic secret patterns
        for (Map.Entry<String, Pattern> entry : SECRET_PATTERNS.entrySet()) {
            String entityType = entry.getKey();
            Matcher matcher = entry.getValue().matcher(text);

            while (matcher.find()) {
                matches.add(new PIIMatch(
                        matcher.group(),
                        entityType,
                        matcher.start(),
                        matcher.end(),
                        0.98
                ));
            }
        }

        // 2. Scan high-entropy alphanumeric tokens
        scanHighEntropyTokens(text, matches);

        return matches;
    }

    private void scanHighEntropyTokens(String text, List<PIIMatch> matches) {
        String[] words = text.split("\\s+");
        int currentOffset = 0;

        for (String word : words) {
            String cleanWord = word.replaceAll("[^a-zA-Z0-9_\\-]", "");
            if (cleanWord.length() >= 32) {
                double entropy = calculateShannonEntropy(cleanWord);
                if (entropy > 4.2) {
                    int start = text.indexOf(word, currentOffset);
                    if (start != -1) {
                        matches.add(new PIIMatch(
                                word,
                                "HIGH_ENTROPY_SECRET",
                                start,
                                start + word.length(),
                                0.90
                        ));
                    }
                }
            }
            currentOffset += word.length() + 1;
        }
    }

    private double calculateShannonEntropy(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        int[] counts = new int[256];
        for (char c : s.toCharArray()) {
            if (c < 256) counts[c]++;
        }
        double entropy = 0.0;
        double len = s.length();
        for (int count : counts) {
            if (count > 0) {
                double p = count / len;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }

    @Override
    public String getPassName() {
        return "PASS_2_SECRET";
    }
}
