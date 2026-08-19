package com.nexus.market.service;

import com.nexus.market.auction.AuctionResult;
import com.nexus.market.auction.TenantBid;
import com.nexus.market.auction.VcgAuctionSolver;
import com.nexus.market.ledger.CreditLedgerService;
import com.nexus.market.pricing.StackelbergPricingEngine;
import com.nexus.market.quota.QuotaCheckResult;
import com.nexus.market.quota.TenantQuota;
import com.nexus.market.quota.TenantQuotaManager;
import com.nexus.market.quota.TenantQuotaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * NexusMarket Pipeline Orchestrator — ties all Member 3 components into a single
 * cohesive processing flow.
 *
 * <p>The pipeline runs as follows for each incoming tenant request:
 *
 * <pre>
 * SanitizedContext (tenantId, tokensNeeded)
 *   → [1] TenantQuotaManager.checkAndReserve()
 *       SUFFICIENT ──► QuotaApproval(approved=true, tokensBorrowed=0)
 *       DEFICIT    ──► [2] StackelbergPricingEngine.computeExchangeRate(U)
 *                       → [3] Build bid list + compute available pool
 *                       → [4] VcgAuctionSolver.solve(bids, poolTokens)
 *                           WINNER ──► [5] TenantQuotaManager.applyBorrowedTokens()
 *                                       → [6] CreditLedgerService.recordTokenBorrow()
 *                                       → QuotaApproval(approved=true, borrowedTokens, creditsCharged)
 *                           LOSER  ──► QuotaApproval(approved=false)
 * </pre>
 *
 * <p>This service uses module-local DTOs ({@link MarketRequest} / {@link MarketApproval})
 * for its public API to avoid runtime classpath coupling to the shared {@code contracts/}
 * package. The {@code contracts/dto/QuotaApproval.java} is the output handed to Member 2.
 */
@Service
public class MarketIncentiveService {

    private static final Logger log = LoggerFactory.getLogger(MarketIncentiveService.class);

    /** Sentinel tenant ID used to represent the shared system surplus pool. */
    private static final String SYSTEM_POOL_TENANT = "SYSTEM_POOL";

    private final TenantQuotaManager quotaManager;
    private final TenantQuotaRepository quotaRepository;
    private final StackelbergPricingEngine pricingEngine;
    private final VcgAuctionSolver auctionSolver;
    private final CreditLedgerService ledgerService;

    public MarketIncentiveService(TenantQuotaManager quotaManager,
                                   TenantQuotaRepository quotaRepository,
                                   StackelbergPricingEngine pricingEngine,
                                   VcgAuctionSolver auctionSolver,
                                   CreditLedgerService ledgerService) {
        this.quotaManager    = quotaManager;
        this.quotaRepository = quotaRepository;
        this.pricingEngine   = pricingEngine;
        this.auctionSolver   = auctionSolver;
        this.ledgerService   = ledgerService;
    }

    /**
     * Main entry point: processes a tenant request through the full NexusMarket pipeline.
     *
     * @param tenantId     the tenant identifier from the {@code X-Tenant-ID} header
     * @param tokensNeeded estimated token cost of the incoming LLM request
     * @return {@link MarketApproval} — approved/denied with full market metadata
     */
    public MarketApproval process(String tenantId, long tokensNeeded) {
        log.info("[Market] START tenant={} tokensNeeded={}", tenantId, tokensNeeded);

        // Step 1: Check the tenant's own quota
        QuotaCheckResult quotaResult = quotaManager.checkAndReserve(tenantId, tokensNeeded);

        if (quotaResult.isSufficient()) {
            log.info("[Market] PASS-THROUGH tenant={} remaining={}", tenantId, quotaResult.getRemainingTokens());
            return MarketApproval.passThrough(tenantId, tokensNeeded, quotaResult.getSystemUtilization());
        }

        // Step 2: Quota deficit — compute Stackelberg exchange rate
        double exchangeRate = pricingEngine.computeExchangeRate(quotaResult.getSystemUtilization());
        log.info("[Market] DEFICIT tenant={} deficit={} exchangeRate={:.4f}",
                tenantId, quotaResult.getDeficitTokens(), exchangeRate);

        // Check whether borrowing is even worthwhile at the current rate
        if (!pricingEngine.shouldBorrow(quotaResult.getDeficitTokens(), exchangeRate)) {
            log.warn("[Market] BORROW_REJECTED tenant={} — rate too high", tenantId);
            return MarketApproval.denied(tenantId, quotaResult.getSystemUtilization(), exchangeRate,
                    "Exchange rate exceeds maximum acceptable threshold");
        }

        // Step 3: Compute available surplus pool & build bid list
        long poolTokens = quotaRepository.computeTotalSurplusTokens();
        if (poolTokens <= 0) {
            log.warn("[Market] POOL_EMPTY — no surplus tokens available for tenant={}", tenantId);
            return MarketApproval.denied(tenantId, quotaResult.getSystemUtilization(), exchangeRate,
                    "Surplus pool is empty — no tokens available for borrowing");
        }

        // In a real system, multiple tenants would submit bids simultaneously.
        // Here we model the single-request case as a one-bid auction.
        List<TenantBid> bids = List.of(
                new TenantBid(tenantId, quotaResult.getDeficitTokens(), exchangeRate)
        );

        // Step 4: Run the VCG second-price auction
        List<AuctionResult> auctionResults = auctionSolver.solve(bids, poolTokens);

        AuctionResult result = auctionResults.stream()
                .filter(r -> r.getTenantId().equals(tenantId) && r.isCleared())
                .findFirst()
                .orElse(null);

        if (result == null) {
            log.warn("[Market] AUCTION_LOST tenant={}", tenantId);
            return MarketApproval.denied(tenantId, quotaResult.getSystemUtilization(), exchangeRate,
                    "Auction was not cleared — insufficient surplus or below min-bid");
        }

        // Step 5: Apply borrowed tokens to tenant's effective quota
        quotaManager.applyBorrowedTokens(tenantId, result.getTokensAllocated());

        // Step 6: Record the borrow in the immutable ledger
        String txId = ledgerService.recordTokenBorrow(
                tenantId, SYSTEM_POOL_TENANT,
                result.getTokensAllocated(), exchangeRate
        );

        log.info("[Market] APPROVED tenant={} borrowed={} credits={:.4f} txId={}",
                tenantId, result.getTokensAllocated(), result.getCreditsPaid(), txId);

        return MarketApproval.fromAuction(tenantId, tokensNeeded, result.getTokensAllocated(),
                result.getCreditsPaid(), exchangeRate, quotaResult.getSystemUtilization(), txId);
    }

    // =========================================================================
    // Module-local DTOs (avoids runtime classpath coupling to contracts/)
    // =========================================================================

    /**
     * Module-local output DTO produced by {@link MarketIncentiveService#process}.
     * The integration layer maps this to {@code contracts/dto/QuotaApproval}.
     */
    public static class MarketApproval {

        private final String  tenantId;
        private final boolean approved;
        private final long    tokensAllocated;
        private final long    tokensBorrowed;
        private final double  creditsCharged;
        private final double  exchangeRate;
        private final boolean auctionCleared;
        private final double  systemUtilization;
        private final String  transactionId;
        private final String  denyReason;
        private final Map<String, String> metadata;

        private MarketApproval(String tenantId, boolean approved, long tokensAllocated,
                               long tokensBorrowed, double creditsCharged, double exchangeRate,
                               boolean auctionCleared, double systemUtilization,
                               String transactionId, String denyReason) {
            this.tenantId          = tenantId;
            this.approved          = approved;
            this.tokensAllocated   = tokensAllocated;
            this.tokensBorrowed    = tokensBorrowed;
            this.creditsCharged    = creditsCharged;
            this.exchangeRate      = exchangeRate;
            this.auctionCleared    = auctionCleared;
            this.systemUtilization = systemUtilization;
            this.transactionId     = transactionId;
            this.denyReason        = denyReason;
            this.metadata          = Map.of(
                    "module", "market-incentives",
                    "version", "1.0.0"
            );
        }

        static MarketApproval passThrough(String tenantId, long tokens, double utilization) {
            return new MarketApproval(tenantId, true, tokens, 0L, 0.0, 0.0, false,
                    utilization, null, null);
        }

        static MarketApproval fromAuction(String tenantId, long tokensAllocated, long tokensBorrowed,
                                          double credits, double rate, double utilization, String txId) {
            return new MarketApproval(tenantId, true, tokensAllocated, tokensBorrowed, credits, rate,
                    true, utilization, txId, null);
        }

        static MarketApproval denied(String tenantId, double utilization, double rate, String reason) {
            return new MarketApproval(tenantId, false, 0L, 0L, 0.0, rate, false, utilization, null, reason);
        }

        // Getters
        public String getTenantId() { return tenantId; }
        public boolean isApproved() { return approved; }
        public long getTokensAllocated() { return tokensAllocated; }
        public long getTokensBorrowed() { return tokensBorrowed; }
        public double getCreditsCharged() { return creditsCharged; }
        public double getExchangeRate() { return exchangeRate; }
        public boolean isAuctionCleared() { return auctionCleared; }
        public double getSystemUtilization() { return systemUtilization; }
        public String getTransactionId() { return transactionId; }
        public String getDenyReason() { return denyReason; }
        public Map<String, String> getMetadata() { return metadata; }
    }
}
