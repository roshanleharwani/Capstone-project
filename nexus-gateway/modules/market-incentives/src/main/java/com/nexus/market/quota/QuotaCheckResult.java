package com.nexus.market.quota;

/**
 * Result of a quota check-and-reserve operation performed by {@link TenantQuotaManager}.
 *
 * <p>Carries all information downstream components need to decide whether to:
 * <ul>
 *   <li>Pass through directly (sufficient tokens from own quota)</li>
 *   <li>Trigger the Stackelberg pricing + VCG auction pipeline (deficit)</li>
 * </ul>
 */
public class QuotaCheckResult {

    /** Whether the tenant has sufficient tokens from its own daily quota. */
    private final boolean sufficient;

    /** The tenant's remaining tokens at the time of the check. */
    private final long remainingTokens;

    /** Tokens required to service the incoming request. */
    private final long tokensNeeded;

    /**
     * Shortfall (tokensNeeded - remainingTokens) when {@code sufficient == false}.
     * Zero when sufficient.
     */
    private final long deficitTokens;

    /**
     * System-wide utilization ratio U ∈ [0, 1] at the time of this check.
     * Computed from aggregated quotas across all tenants and fed to the Stackelberg engine.
     */
    private final double systemUtilization;

    public QuotaCheckResult(boolean sufficient, long remainingTokens, long tokensNeeded,
                            long deficitTokens, double systemUtilization) {
        this.sufficient        = sufficient;
        this.remainingTokens   = remainingTokens;
        this.tokensNeeded      = tokensNeeded;
        this.deficitTokens     = deficitTokens;
        this.systemUtilization = systemUtilization;
    }

    // ---- Factory helpers -------------------------------------------------

    /** Constructs a "pass-through" result — tenant quota is sufficient. */
    public static QuotaCheckResult sufficient(long remainingTokens, long tokensNeeded,
                                              double systemUtilization) {
        return new QuotaCheckResult(true, remainingTokens, tokensNeeded, 0L, systemUtilization);
    }

    /** Constructs a "deficit" result — market pipeline must be triggered. */
    public static QuotaCheckResult deficit(long remainingTokens, long tokensNeeded,
                                           double systemUtilization) {
        long shortfall = tokensNeeded - remainingTokens;
        return new QuotaCheckResult(false, remainingTokens, tokensNeeded, shortfall, systemUtilization);
    }

    // ---- Getters ---------------------------------------------------------

    public boolean isSufficient() { return sufficient; }
    public long getRemainingTokens() { return remainingTokens; }
    public long getTokensNeeded() { return tokensNeeded; }
    public long getDeficitTokens() { return deficitTokens; }
    public double getSystemUtilization() { return systemUtilization; }
}
