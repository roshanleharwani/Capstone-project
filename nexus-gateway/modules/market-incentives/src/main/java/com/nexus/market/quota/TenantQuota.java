package com.nexus.market.quota;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity representing a tenant's daily token quota record.
 *
 * <p>Mapped to the {@code tenant_quotas} PostgreSQL table. Row-Level Security (RLS)
 * on this table ensures a tenant's service account can only read/update its own row.
 *
 * <p>Schema (informational — managed by DBA / migration scripts):
 * <pre>
 *   CREATE TABLE tenant_quotas (
 *       tenant_id       VARCHAR(128) PRIMARY KEY,
 *       daily_limit     BIGINT       NOT NULL DEFAULT 1000000,
 *       used_tokens     BIGINT       NOT NULL DEFAULT 0,
 *       priority_credits NUMERIC(18,4) NOT NULL DEFAULT 0,
 *       reset_at        TIMESTAMPTZ  NOT NULL
 *   );
 *   ALTER TABLE tenant_quotas ENABLE ROW LEVEL SECURITY;
 * </pre>
 */
@Entity
@Table(name = "tenant_quotas")
public class TenantQuota {

    /** Tenant identifier sourced from the X-Tenant-ID request header. */
    @Id
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    /** Maximum tokens this tenant is allowed to consume per day. */
    @Column(name = "daily_limit", nullable = false)
    private long dailyLimit;

    /** Tokens consumed so far in the current quota window. */
    @Column(name = "used_tokens", nullable = false)
    private long usedTokens;

    /**
     * Gateway credits accumulated by lending surplus tokens to other tenants.
     * Redeemable for priority queue access or higher-tier model routing.
     */
    @Column(name = "priority_credits", nullable = false)
    private double priorityCredits;

    /** UTC timestamp when the daily quota window resets (typically midnight). */
    @Column(name = "reset_at", nullable = false)
    private Instant resetAt;

    public TenantQuota() {}

    public TenantQuota(String tenantId, long dailyLimit, long usedTokens,
                       double priorityCredits, Instant resetAt) {
        this.tenantId       = tenantId;
        this.dailyLimit     = dailyLimit;
        this.usedTokens     = usedTokens;
        this.priorityCredits = priorityCredits;
        this.resetAt        = resetAt;
    }

    // ---- Derived helpers ------------------------------------------------

    /** Returns the number of tokens remaining in the current quota window. */
    public long remainingTokens() {
        return Math.max(0L, dailyLimit - usedTokens);
    }

    /** Returns true when this tenant has surplus tokens available to lend. */
    public boolean hasSurplus() {
        return remainingTokens() > 0;
    }

    // ---- Getters / Setters ----------------------------------------------

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public long getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(long dailyLimit) { this.dailyLimit = dailyLimit; }

    public long getUsedTokens() { return usedTokens; }
    public void setUsedTokens(long usedTokens) { this.usedTokens = usedTokens; }

    public double getPriorityCredits() { return priorityCredits; }
    public void setPriorityCredits(double priorityCredits) { this.priorityCredits = priorityCredits; }

    public Instant getResetAt() { return resetAt; }
    public void setResetAt(Instant resetAt) { this.resetAt = resetAt; }
}
