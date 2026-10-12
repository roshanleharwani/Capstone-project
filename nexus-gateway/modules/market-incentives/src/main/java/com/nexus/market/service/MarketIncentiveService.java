package com.nexus.market.service;

import com.nexus.market.auction.AuctionResult;
import com.nexus.market.auction.PendingBidRegistry;
import com.nexus.market.auction.TenantBid;
import com.nexus.market.auction.VcgAuctionSolver;
import com.nexus.market.ledger.CreditLedgerService;
import com.nexus.market.pricing.StackelbergPricingEngine;
import com.nexus.market.quota.LenderContribution;
import com.nexus.market.quota.QuotaCheckResult;
import com.nexus.market.quota.TenantQuotaManager;
import com.nexus.market.quota.TenantQuotaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * NexusMarket pipeline: quota check → Stackelberg price → multi-bid VCG auction →
 * lender deduction → borrow grant/reserve → ledger at VCG second-price.
 */
@Service
public class MarketIncentiveService {

    private static final Logger log = LoggerFactory.getLogger(MarketIncentiveService.class);

    private final TenantQuotaManager quotaManager;
    private final TenantQuotaRepository quotaRepository;
    private final StackelbergPricingEngine pricingEngine;
    private final VcgAuctionSolver auctionSolver;
    private final CreditLedgerService ledgerService;
    private final PendingBidRegistry pendingBidRegistry;

    public MarketIncentiveService(TenantQuotaManager quotaManager,
                                  TenantQuotaRepository quotaRepository,
                                  StackelbergPricingEngine pricingEngine,
                                  VcgAuctionSolver auctionSolver,
                                  CreditLedgerService ledgerService,
                                  PendingBidRegistry pendingBidRegistry) {
        this.quotaManager = quotaManager;
        this.quotaRepository = quotaRepository;
        this.pricingEngine = pricingEngine;
        this.auctionSolver = auctionSolver;
        this.ledgerService = ledgerService;
        this.pendingBidRegistry = pendingBidRegistry;
    }

    @Transactional
    public MarketApproval process(String tenantId, long tokensNeeded) {
        if (!Boolean.parseBoolean(System.getProperty("nexus.settings.stackelberg", "true"))) {
            return MarketApproval.passThrough(tenantId, tokensNeeded, 50.0, 0L);
        }

        log.info("[Market] START tenant={} tokensNeeded={}", tenantId, tokensNeeded);

        QuotaCheckResult quotaResult = quotaManager.checkAndReserve(tenantId, tokensNeeded);

        if (quotaResult.isSufficient()) {
            pendingBidRegistry.clear(tenantId);
            log.info("[Market] PASS-THROUGH tenant={} remaining={}", tenantId, quotaResult.getRemainingTokens());
            return MarketApproval.passThrough(tenantId, tokensNeeded, quotaResult.getSystemUtilization(),
                    quotaResult.getRemainingTokens());
        }

        double exchangeRate = pricingEngine.computeExchangeRate(quotaResult.getSystemUtilization());
        log.info("[Market] DEFICIT tenant={} deficit={} exchangeRate={}",
                tenantId, quotaResult.getDeficitTokens(), exchangeRate);

        if (!pricingEngine.shouldBorrow(quotaResult.getDeficitTokens(), exchangeRate)) {
            log.warn("[Market] BORROW_REJECTED tenant={} — rate/deficit outside borrow policy", tenantId);
            return MarketApproval.denied(tenantId, quotaResult.getSystemUtilization(), exchangeRate,
                    "Exchange rate exceeds maximum acceptable threshold");
        }

        long poolTokens = quotaManager.computeLendablePool(tenantId,
                q -> pricingEngine.shouldLend(exchangeRate, q.remainingTokens()));
        if (poolTokens <= 0) {
            log.warn("[Market] POOL_EMPTY — no lendable surplus for tenant={}", tenantId);
            return MarketApproval.denied(tenantId, quotaResult.getSystemUtilization(), exchangeRate,
                    "Surplus pool is empty — no tokens available for borrowing");
        }

        // Multi-tenant sealed bids: enqueue this request and auction against all active bids.
        TenantBid ownBid = new TenantBid(tenantId, quotaResult.getDeficitTokens(), exchangeRate);
        pendingBidRegistry.submit(ownBid);
        List<TenantBid> bids = pendingBidRegistry.snapshotActive();
        if (bids.stream().noneMatch(b -> b.getTenantId().equals(tenantId))) {
            bids = List.of(ownBid);
        }

        List<AuctionResult> auctionResults = auctionSolver.solve(bids, poolTokens);
        AuctionResult result = auctionResults.stream()
                .filter(r -> r.getTenantId().equals(tenantId) && r.isCleared())
                .findFirst()
                .orElse(null);

        if (result == null || result.getTokensAllocated() < quotaResult.getDeficitTokens()) {
            log.warn("[Market] AUCTION_LOST_OR_PARTIAL tenant={} allocated={}",
                    tenantId, result == null ? 0 : result.getTokensAllocated());
            return MarketApproval.denied(tenantId, quotaResult.getSystemUtilization(), exchangeRate,
                    "Auction was not cleared — insufficient surplus or below min-bid");
        }

        // Settle every full winner in this round so competing bids are not left hanging.
        Set<String> settled = new HashSet<>();
        String primaryTxId = null;
        double primaryCredits = 0.0;
        long primaryBorrowed = 0L;

        Map<String, AuctionResult> winners = auctionResults.stream()
                .filter(AuctionResult::isCleared)
                .collect(Collectors.toMap(AuctionResult::getTenantId, r -> r, (a, b) -> a));

        for (AuctionResult winner : winners.values()) {
            TenantBid bid = bids.stream()
                    .filter(b -> b.getTenantId().equals(winner.getTenantId()))
                    .findFirst()
                    .orElse(null);
            if (bid == null || winner.getTokensAllocated() < bid.getTokensNeeded()) {
                continue; // require full deficit coverage
            }

            List<LenderContribution> contributions = quotaManager.deductFromLenders(
                    winner.getTenantId(),
                    winner.getTokensAllocated(),
                    q -> pricingEngine.shouldLend(exchangeRate, q.remainingTokens())
            );
            long collected = contributions.stream().mapToLong(LenderContribution::tokensLent).sum();
            if (collected < winner.getTokensAllocated()) {
                log.warn("[Market] SETTLEMENT_ABORT tenant={} collected={}/{}",
                        winner.getTenantId(), collected, winner.getTokensAllocated());
                // Best-effort: refund lenders already deducted in this abort path
                for (LenderContribution c : contributions) {
                    quotaManager.releaseReservation(c.lenderTenantId(), c.tokensLent());
                }
                continue;
            }

            long requestTokens = winner.getTenantId().equals(tenantId) ? tokensNeeded : bid.getTokensNeeded();
            // For other winners, reserve their bid size (deficit-only path had not reserved yet).
            // Their remaining before borrow was tokensNeeded_bid - deficit... We only know deficit.
            // grantBorrowAndReserve adds borrowed then reserves requestTokens.
            // For non-current tenants we reserve borrowed amount worth of capacity by using
            // deficit + 0 remaining approximation: reserve = borrowed (covers their deficit request slice).
            if (!winner.getTenantId().equals(tenantId)) {
                requestTokens = winner.getTokensAllocated();
            }

            quotaManager.grantBorrowAndReserve(winner.getTenantId(), winner.getTokensAllocated(), requestTokens);

            double vcgRatePerK = winner.getTokensAllocated() > 0
                    ? (winner.getCreditsPaid() * 1000.0) / winner.getTokensAllocated()
                    : exchangeRate;

            String txId = null;
            for (LenderContribution c : contributions) {
                String partTx = ledgerService.recordTokenBorrow(
                        winner.getTenantId(),
                        c.lenderTenantId(),
                        c.tokensLent(),
                        vcgRatePerK
                );
                if (txId == null) {
                    txId = partTx;
                }
            }

            settled.add(winner.getTenantId());
            if (winner.getTenantId().equals(tenantId)) {
                primaryTxId = txId;
                primaryCredits = winner.getCreditsPaid();
                primaryBorrowed = winner.getTokensAllocated();
            }

            log.info("[Market] SETTLED tenant={} borrowed={} vcgRate={} txId={}",
                    winner.getTenantId(), winner.getTokensAllocated(), vcgRatePerK, txId);
        }

        pendingBidRegistry.clear(settled);

        if (!settled.contains(tenantId)) {
            return MarketApproval.denied(tenantId, quotaResult.getSystemUtilization(), exchangeRate,
                    "Auction was not cleared — insufficient surplus or below min-bid");
        }

        long remaining = quotaRepository.findByTenantId(tenantId)
                .map(q -> q.remainingTokens())
                .orElse(0L);
        return MarketApproval.fromAuction(tenantId, tokensNeeded, primaryBorrowed,
                primaryCredits, exchangeRate, quotaResult.getSystemUtilization(), primaryTxId, remaining);
    }

    public static class MarketApproval {

        private final String tenantId;
        private final boolean approved;
        private final long tokensAllocated;
        private final long tokensBorrowed;
        private final double creditsCharged;
        private final double exchangeRate;
        private final boolean auctionCleared;
        private final double systemUtilization;
        private final String transactionId;
        private final String denyReason;
        private final long quotaRemaining;
        private final Map<String, String> metadata;

        private MarketApproval(String tenantId, boolean approved, long tokensAllocated,
                               long tokensBorrowed, double creditsCharged, double exchangeRate,
                               boolean auctionCleared, double systemUtilization,
                               String transactionId, String denyReason, long quotaRemaining) {
            this.tenantId = tenantId;
            this.approved = approved;
            this.tokensAllocated = tokensAllocated;
            this.tokensBorrowed = tokensBorrowed;
            this.creditsCharged = creditsCharged;
            this.exchangeRate = exchangeRate;
            this.auctionCleared = auctionCleared;
            this.systemUtilization = systemUtilization;
            this.transactionId = transactionId;
            this.denyReason = denyReason;
            this.quotaRemaining = quotaRemaining;
            this.metadata = Map.of(
                    "module", "market-incentives",
                    "version", "1.1.0"
            );
        }

        static MarketApproval passThrough(String tenantId, long tokens, double utilization, long remaining) {
            return new MarketApproval(tenantId, true, tokens, 0L, 0.0, 0.0, false,
                    utilization, null, null, remaining);
        }

        static MarketApproval fromAuction(String tenantId, long tokensAllocated, long tokensBorrowed,
                                          double credits, double rate, double utilization, String txId,
                                          long remaining) {
            return new MarketApproval(tenantId, true, tokensAllocated, tokensBorrowed, credits, rate,
                    true, utilization, txId, null, remaining);
        }

        static MarketApproval denied(String tenantId, double utilization, double rate, String reason) {
            return new MarketApproval(tenantId, false, 0L, 0L, 0.0, rate, false, utilization, null, reason, 0L);
        }

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
        public long getQuotaRemaining() { return quotaRemaining; }
        public Map<String, String> getMetadata() { return metadata; }
    }
}
