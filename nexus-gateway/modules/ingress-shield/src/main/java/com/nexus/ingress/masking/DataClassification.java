package com.nexus.ingress.masking;

/**
 * Data Sensitivity Classification enum for ZTEP Policy Enforcement.
 * - PUBLIC: Unrestricted payload. Sent directly through pipeline.
 * - INTERNAL: Masked using session-stable type-preserving placeholders ({{PERSON_1}}).
 * - RESTRICTED: High-sensitivity enterprise payload. Hard-routed to Tier 1 Self-Hosted vLLM ONLY.
 */
public enum DataClassification {
    PUBLIC,
    INTERNAL,
    RESTRICTED
}
