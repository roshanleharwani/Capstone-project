package com.nexus.market.quota;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Service responsible for intercepting tenant requests and verifying available
 * daily API token balances.
 *
 * <p><b>Core responsibility (Task 1):</b> Reads the {@code X-Tenant-ID} header,
 * looks up the tenant's quota record, and either:
 * <ul>
 *   <li>Decrements the token count and returns a {@link QuotaCheckResult#sufficient} result</li>
 *   <li>Returns a {@link QuotaCheckResult#deficit} result to trigger the NexusMarket pipeline</li>
 * </ul>
 *
 * <p>System-wide utilization {@code U} is computed as the fraction of all tenants'
 * combined token budget that has been consumed. This value is forwarded to the
 * {@code StackelbergPricingEngine} to set the current exchange rate.
 */
@Service
public class TenantQuotaManager {

    private static final Logger log = LoggerFactory.getLogger(TenantQuotaManager.class);

    private final TenantQuotaRepository quotaRepository;

    @Value("${market.quota.default-daily-limit:1000000}")
    private long defaultDailyLimit;

    public TenantQuotaManager(TenantQuotaRepository quotaRepository) {
        this.quotaRepository = quotaRepository;
    }

    /**
     * Checks whether {@code tenantId} has enough tokens for the request and, if so,
     * atomically reserves (decrements) them from the quota record.
     *
     * <p>If the tenant record does not exist yet, a new record is auto-provisioned
     * with {@code defaultDailyLimit} tokens and a reset time of midnight UTC today+1.
     *
     * @param tenantId    the value of the {@code X-Tenant-ID} request header
     * @param tokensNeeded estimated token cost of the incoming request
     * @return {@link QuotaCheckResult} indicating surplus or deficit state
     */
    @Transactional
    public QuotaCheckResult checkAndReserve(String tenantId, long tokensNeeded) {
        TenantQuota quota = quotaRepository.findByTenantId(tenantId)
                .orElseGet(() -> provisionNewTenant(tenantId));

        // Auto-reset quota window if the reset timestamp has passed
        if (Instant.now().isAfter(quota.getResetAt())) {
            log.info("[QuotaManager] Resetting quota window for tenant={}", tenantId);
            quota.setUsedTokens(0L);
            quota.setResetAt(Instant.now().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS));
        }

        double systemUtilization = computeSystemUtilization();
        long remaining = quota.remainingTokens();

        if (remaining >= tokensNeeded) {
            // Sufficient — reserve the tokens optimistically
            quota.setUsedTokens(quota.getUsedTokens() + tokensNeeded);
            quotaRepository.save(quota);
            log.debug("[QuotaManager] PASS tenant={} reserved={} remaining={}",
                    tenantId, tokensNeeded, remaining - tokensNeeded);
            return QuotaCheckResult.sufficient(remaining - tokensNeeded, tokensNeeded, systemUtilization);
        } else {
            // Deficit — do NOT decrement; let the market pipeline handle allocation
            log.info("[QuotaManager] DEFICIT tenant={} needed={} remaining={} deficit={}",
                    tenantId, tokensNeeded, remaining, tokensNeeded - remaining);
            return QuotaCheckResult.deficit(remaining, tokensNeeded, systemUtilization);
        }
    }

    /**
     * Releases previously reserved tokens back to the tenant's quota.
     * Called by the Cascade Guard if the request is escalated or fails.
     *
     * @param tenantId    the tenant whose reservation to release
     * @param tokensToReturn number of tokens to release
     */
    @Transactional
    public void releaseReservation(String tenantId, long tokensToReturn) {
        quotaRepository.findByTenantId(tenantId).ifPresent(quota -> {
            quota.setUsedTokens(Math.max(0L, quota.getUsedTokens() - tokensToReturn));
            quotaRepository.save(quota);
            log.debug("[QuotaManager] RELEASE tenant={} returned={}", tenantId, tokensToReturn);
        });
    }

    /**
     * Adds borrowed tokens to a tenant's effective daily limit after a successful
     * auction allocation, allowing the request to proceed.
     *
     * @param tenantId       the borrowing tenant
     * @param borrowedTokens tokens granted from the surplus pool
     */
    @Transactional
    public void applyBorrowedTokens(String tenantId, long borrowedTokens) {
        quotaRepository.findByTenantId(tenantId).ifPresent(quota -> {
            quota.setDailyLimit(quota.getDailyLimit() + borrowedTokens);
            quotaRepository.save(quota);
            log.info("[QuotaManager] BORROW_GRANT tenant={} extra={}", tenantId, borrowedTokens);
        });
    }

    // ---- Private helpers -------------------------------------------------

    /**
     * Computes system-wide utilization U = total_used / total_limit across all tenants.
     * Returns 0.0 if no tenants are registered.
     */
    private double computeSystemUtilization() {
        // Fetch aggregate stats using JPQL — avoids loading all rows into memory
        long totalSurplus = quotaRepository.computeTotalSurplusTokens();
        // Derive totalLimit from surplus + a simple aggregate across all quotas
        // For simplicity: U = 1 - (totalSurplus / defaultDailyLimit_per_tenant * tenantCount)
        // In production this would be a dedicated aggregate query; here we use a heuristic
        long count = quotaRepository.count();
        if (count == 0) return 0.0;
        long estimatedTotalLimit = count * defaultDailyLimit;
        if (estimatedTotalLimit == 0) return 0.0;
        double utilization = 1.0 - ((double) totalSurplus / estimatedTotalLimit);
        return Math.max(0.0, Math.min(1.0, utilization));
    }

    /** Auto-provisions a new TenantQuota with default limits. */
    private TenantQuota provisionNewTenant(String tenantId) {
        log.info("[QuotaManager] Auto-provisioning new tenant={}", tenantId);
        Instant resetAt = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
        TenantQuota quota = new TenantQuota(tenantId, defaultDailyLimit, 0L, 0.0, resetAt);
        return quotaRepository.save(quota);
    }
}
