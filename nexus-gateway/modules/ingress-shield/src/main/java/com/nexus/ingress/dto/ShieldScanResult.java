package com.nexus.ingress.dto;

/**
 * Result of Prompt Injection Shield inspection.
 */
public class ShieldScanResult {

    private final boolean safe;
    private final String category;
    private final double riskScore;
    private final String reason;

    public ShieldScanResult(boolean safe, String category, double riskScore, String reason) {
        this.safe = safe;
        this.category = category;
        this.riskScore = riskScore;
        this.reason = reason;
    }

    public static ShieldScanResult safe() {
        return new ShieldScanResult(true, "NONE", 0.0, "Prompt passed security evaluation");
    }

    public static ShieldScanResult violation(String category, double riskScore, String reason) {
        return new ShieldScanResult(false, category, riskScore, reason);
    }

    public boolean isSafe() {
        return safe;
    }

    public String getCategory() {
        return category;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public String getReason() {
        return reason;
    }
}
