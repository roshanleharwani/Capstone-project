package com.nexus.market.quota;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Service responsible for intercepting tenant requests and verifying available
 * daily API token balances.
 */
@Service
public class TenantQuotaManager {

    private static final Logger log = LoggerFactory.getLogger(TenantQuotaManager.class);

    private final TenantQuotaRepository quotaRepository;

    @Value("${market.quota.default-daily-limit:1000000}")
    private long defaultDailyLimit;

    /**
     * Fraction of a lender's remaining surplus that may be committed in one auction
     * (keeps lenders from being stripped bare).
     */
    @Value("${market.quota.lend-fraction:0.5}")
    private double lendFraction;

    public TenantQuotaManager(TenantQuotaRepository quotaRepository) {
        this.quotaRepository = quotaRepository;
    }

    @Transactional
    public QuotaCheckResult checkAndReserve(String tenantId, long tokensNeeded) {
        TenantQuota quota = quotaRepository.findByTenantId(tenantId)
                .orElseGet(() -> provisionNewTenant(tenantId));

        if (Instant.now().isAfter(quota.getResetAt())) {
            log.info("[QuotaManager] Resetting quota window for tenant={}", tenantId);
            quota.setUsedTokens(0L);
            quota.setResetAt(Instant.now().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS));
        }

        double systemUtilization = computeSystemUtilization();
        long remaining = quota.remainingTokens();

        if (remaining >= tokensNeeded) {
            quota.setUsedTokens(quota.getUsedTokens() + tokensNeeded);
            quotaRepository.save(quota);
            log.debug("[QuotaManager] PASS tenant={} reserved={} remaining={}",
                    tenantId, tokensNeeded, remaining - tokensNeeded);
            return QuotaCheckResult.sufficient(remaining - tokensNeeded, tokensNeeded, systemUtilization);
        }

        log.info("[QuotaManager] DEFICIT tenant={} needed={} remaining={} deficit={}",
                tenantId, tokensNeeded, remaining, tokensNeeded - remaining);
        return QuotaCheckResult.deficit(remaining, tokensNeeded, systemUtilization);
    }

    @Transactional
    public void releaseReservation(String tenantId, long tokensToReturn) {
        quotaRepository.findByTenantId(tenantId).ifPresent(quota -> {
            quota.setUsedTokens(Math.max(0L, quota.getUsedTokens() - tokensToReturn));
            quotaRepository.save(quota);
            log.debug("[QuotaManager] RELEASE tenant={} returned={}", tenantId, tokensToReturn);
        });
    }

    /**
     * Increases the borrower's daily limit by the auction allocation, then reserves
     * the tokens for the current request so the win actually consumes budget.
     */
    @Transactional
    public void grantBorrowAndReserve(String tenantId, long borrowedTokens, long tokensNeeded) {
        TenantQuota quota = quotaRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("Unknown tenant: " + tenantId));

        if (borrowedTokens > 0) {
            quota.setDailyLimit(quota.getDailyLimit() + borrowedTokens);
        }
        long available = quota.remainingTokens();
        long reserve = Math.min(tokensNeeded, available);
        quota.setUsedTokens(quota.getUsedTokens() + reserve);
        quotaRepository.save(quota);
        log.info("[QuotaManager] BORROW_GRANT_RESERVE tenant={} borrowed={} reserved={} remaining={}",
                tenantId, borrowedTokens, reserve, quota.remainingTokens());
    }

    /** @deprecated Prefer {@link #grantBorrowAndReserve(String, long, long)}. */
    @Transactional
    public void applyBorrowedTokens(String tenantId, long borrowedTokens) {
        grantBorrowAndReserve(tenantId, borrowedTokens, 0L);
    }

    /**
     * Surplus tokens that eligible lenders are willing to commit (excludes the borrower).
     */
    public long computeLendablePool(String borrowerTenantId, Predicate<TenantQuota> lenderEligible) {
        return findEligibleLenders(borrowerTenantId, lenderEligible).stream()
                .mapToLong(this::lendableFrom)
                .sum();
    }

    /**
     * Deducts {@code tokensNeeded} from surplus lenders (largest surplus first).
     * Marks committed tokens by increasing each lender's {@code usedTokens}.
     */
    @Transactional
    public List<LenderContribution> deductFromLenders(String borrowerTenantId,
                                                      long tokensNeeded,
                                                      Predicate<TenantQuota> lenderEligible) {
        if (tokensNeeded <= 0) {
            return List.of();
        }

        List<TenantQuota> lenders = findEligibleLenders(borrowerTenantId, lenderEligible);
        long remaining = tokensNeeded;
        List<LenderContribution> contributions = new ArrayList<>();

        for (TenantQuota lender : lenders) {
            if (remaining <= 0) {
                break;
            }
            long lendable = lendableFrom(lender);
            if (lendable <= 0) {
                continue;
            }
            long take = Math.min(lendable, remaining);
            lender.setUsedTokens(lender.getUsedTokens() + take);
            quotaRepository.save(lender);
            contributions.add(new LenderContribution(lender.getTenantId(), take));
            remaining -= take;
            log.info("[QuotaManager] LEND tenant={} lent={} to borrower={}",
                    lender.getTenantId(), take, borrowerTenantId);
        }

        if (remaining > 0) {
            log.warn("[QuotaManager] Could only collect {} of {} requested lend tokens",
                    tokensNeeded - remaining, tokensNeeded);
        }
        return contributions;
    }

    private List<TenantQuota> findEligibleLenders(String borrowerTenantId,
                                                  Predicate<TenantQuota> lenderEligible) {
        return quotaRepository.findTenantsWithSurplus().stream()
                .filter(q -> !q.getTenantId().equals(borrowerTenantId))
                .filter(q -> lenderEligible == null || lenderEligible.test(q))
                .sorted(Comparator.comparingLong(TenantQuota::remainingTokens).reversed())
                .toList();
    }

    private long lendableFrom(TenantQuota lender) {
        double fraction = Math.max(0.0, Math.min(1.0, lendFraction));
        return (long) Math.floor(lender.remainingTokens() * fraction);
    }

    private double computeSystemUtilization() {
        long totalSurplus = quotaRepository.computeTotalSurplusTokens();
        long count = quotaRepository.count();
        if (count == 0) return 0.0;
        long estimatedTotalLimit = count * defaultDailyLimit;
        if (estimatedTotalLimit == 0) return 0.0;
        double utilization = 1.0 - ((double) totalSurplus / estimatedTotalLimit);
        return Math.max(0.0, Math.min(1.0, utilization));
    }

    private TenantQuota provisionNewTenant(String tenantId) {
        log.info("[QuotaManager] Auto-provisioning new tenant={}", tenantId);
        Instant resetAt = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
        TenantQuota quota = new TenantQuota(tenantId, defaultDailyLimit, 0L, 0.0, resetAt);
        return quotaRepository.save(quota);
    }
}
