package com.nexus.ingress.masking;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Do-Not-Mask allowlist containing generic public geography, dates, common technology nouns,
 * and system terms that must remain unmasked to preserve model task comprehension.
 */
@Component
public class DoNotMaskAllowlist {

    private final Set<String> allowlist = ConcurrentHashMap.newKeySet();

    public DoNotMaskAllowlist() {
        // Generic geography
        allowlist.add("United States");
        allowlist.add("USA");
        allowlist.add("India");
        allowlist.add("California");
        allowlist.add("New York");
        allowlist.add("London");
        allowlist.add("Earth");

        // Public systems / technical terms
        allowlist.add("OpenAI");
        allowlist.add("Google");
        allowlist.add("Microsoft");
        allowlist.add("NexusGateway");
        allowlist.add("vLLM");
        allowlist.add("Kubernetes");
        allowlist.add("Docker");
        allowlist.add("Java");
        allowlist.add("Spring Boot");

        // Common dates / units
        allowlist.add("Monday");
        allowlist.add("Tuesday");
        allowlist.add("Wednesday");
        allowlist.add("Thursday");
        allowlist.add("Friday");
        allowlist.add("Saturday");
        allowlist.add("Sunday");
        allowlist.add("January");
        allowlist.add("February");
        allowlist.add("March");
        allowlist.add("April");
        allowlist.add("May");
        allowlist.add("June");
        allowlist.add("July");
        allowlist.add("August");
        allowlist.add("September");
        allowlist.add("October");
        allowlist.add("November");
        allowlist.add("December");
    }

    public boolean shouldAllow(String term) {
        if (term == null || term.isBlank()) {
            return true;
        }
        return allowlist.contains(term.trim());
    }

    public void addTerm(String term) {
        if (term != null && !term.isBlank()) {
            allowlist.add(term.trim());
        }
    }

    public void removeTerm(String term) {
        if (term != null) {
            allowlist.remove(term.trim());
        }
    }
}
