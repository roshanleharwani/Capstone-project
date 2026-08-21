package com.nexus.market.service;

import com.nexus.market.auction.AuctionResult;
import com.nexus.market.auction.TenantBid;
import com.nexus.market.auction.VcgAuctionSolver;
import com.nexus.market.ledger.CreditLedgerService;
import com.nexus.market.pricing.StackelbergPricingEngine;
import com.nexus.market.quota.QuotaCheckResult;
import com.nexus.market.quota.TenantQuotaManager;
import com.nexus.market.quota.TenantQuotaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MarketIncentiveService} — the pipeline orchestrator.
 *
 * <p>All components are mocked. Tests verify that the correct pipeline branches
 * are taken and that the output {@link MarketIncentiveService.MarketApproval} DTO
 * is populated correctly in each scenario.
 *
 * <p>Test coverage:
 * <ul>
 *   <li>Sufficient quota → pass-through (no pricing/auction/ledger called)</li>
 *   <li>Deficit + auction win → full pipeline executed, approval returned</li>
 *   <li>Deficit + auction loss → denied result</li>
 *   <li>Deficit + empty pool → denied result before auction runs</li>
 *   <li>Deficit + rate too high → denied before auction</li>
 *   <li>tenantId and tokensAllocated propagated to output correctly</li>
 *   <li>transactionId is populated on auction-cleared approvals</li>
 *   <li>tokensBorrowed and creditsCharged are set correctly</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MarketIncentiveService Unit Tests")
class MarketIncentiveServiceTest {

    @Mock private TenantQuotaManager      quotaManager;
    @Mock private TenantQuotaRepository   quotaRepository;
    @Mock private StackelbergPricingEngine pricingEngine;
    @Mock private VcgAuctionSolver         auctionSolver;
    @Mock private CreditLedgerService      ledgerService;

    @InjectMocks
    private MarketIncentiveService service;

    // ---- Pass-through path ----------------------------------------------

    @Test
    @DisplayName("Sufficient quota → approved pass-through, no auction or ledger called")
    void process_sufficientQuota_passThroughApproval() {
        when(quotaManager.checkAndReserve("cs-dept", 50_000L))
                .thenReturn(QuotaCheckResult.sufficient(950_000L, 50_000L, 0.4));

        MarketIncentiveService.MarketApproval result = service.process("cs-dept", 50_000L);

        assertThat(result.isApproved()).isTrue();
        assertThat(result.getTenantId()).isEqualTo("cs-dept");
        assertThat(result.getTokensAllocated()).isEqualTo(50_000L);
        assertThat(result.getTokensBorrowed()).isEqualTo(0L);
        assertThat(result.getCreditsCharged()).isZero();
        assertThat(result.isAuctionCleared()).isFalse();
        assertThat(result.getTransactionId()).isNull();

        // Pricing, auction, ledger should NOT be touched
        verifyNoInteractions(pricingEngine, auctionSolver, ledgerService);
    }

    // ---- Full auction-cleared path ---------------------------------------

    @Test
    @DisplayName("Deficit + winning auction → approved with borrowed tokens and ledger recorded")
    void process_deficitAndAuctionWin_approvedWithBorrowedTokens() {
        // Step 1: quota is insufficient
        when(quotaManager.checkAndReserve("cs-dept", 200_000L))
                .thenReturn(QuotaCheckResult.deficit(50_000L, 200_000L, 0.85));

        // Step 2: Stackelberg pricing
        when(pricingEngine.computeExchangeRate(0.85)).thenReturn(2.5);
        when(pricingEngine.shouldBorrow(150_000L, 2.5)).thenReturn(true);

        // Step 3: pool has surplus
        when(quotaRepository.computeTotalSurplusTokens()).thenReturn(500_000L);

        // Step 4: auction clears
        when(auctionSolver.solve(anyList(), eq(500_000L)))
                .thenReturn(List.of(AuctionResult.winner("cs-dept", 150_000L, 375.0)));

        // Step 6: ledger returns a txId
        when(ledgerService.recordTokenBorrow("cs-dept", "SYSTEM_POOL", 150_000L, 2.5))
                .thenReturn("tx-abc-123");

        MarketIncentiveService.MarketApproval result = service.process("cs-dept", 200_000L);

        assertThat(result.isApproved()).isTrue();
        assertThat(result.isAuctionCleared()).isTrue();
        assertThat(result.getTokensBorrowed()).isEqualTo(150_000L);
        assertThat(result.getCreditsCharged()).isCloseTo(375.0, within(0.001));
        assertThat(result.getExchangeRate()).isCloseTo(2.5, within(0.001));
        assertThat(result.getTransactionId()).isEqualTo("tx-abc-123");

        // Verify quota was updated with borrowed tokens (Step 5)
        verify(quotaManager).applyBorrowedTokens("cs-dept", 150_000L);
        // Verify ledger was recorded (Step 6)
        verify(ledgerService).recordTokenBorrow("cs-dept", "SYSTEM_POOL", 150_000L, 2.5);
    }

    // ---- Auction loss path ----------------------------------------------

    @Test
    @DisplayName("Deficit + losing auction → denied result, no ledger entry")
    void process_deficitAndAuctionLoss_deniedResult() {
        when(quotaManager.checkAndReserve("cs-dept", 200_000L))
                .thenReturn(QuotaCheckResult.deficit(0L, 200_000L, 0.9));
        when(pricingEngine.computeExchangeRate(0.9)).thenReturn(2.8);
        when(pricingEngine.shouldBorrow(200_000L, 2.8)).thenReturn(true);
        when(quotaRepository.computeTotalSurplusTokens()).thenReturn(100_000L);
        // Auction: cs-dept loses
        when(auctionSolver.solve(anyList(), eq(100_000L)))
                .thenReturn(List.of(AuctionResult.loser("cs-dept")));

        MarketIncentiveService.MarketApproval result = service.process("cs-dept", 200_000L);

        assertThat(result.isApproved()).isFalse();
        assertThat(result.isAuctionCleared()).isFalse();
        assertThat(result.getTransactionId()).isNull();

        verifyNoInteractions(ledgerService);
        verify(quotaManager, never()).applyBorrowedTokens(any(), anyLong());
    }

    // ---- Empty pool path ------------------------------------------------

    @Test
    @DisplayName("Deficit + empty pool → denied immediately, auction never called")
    void process_emptyPool_deniedBeforeAuction() {
        when(quotaManager.checkAndReserve("cs-dept", 100_000L))
                .thenReturn(QuotaCheckResult.deficit(0L, 100_000L, 0.95));
        when(pricingEngine.computeExchangeRate(0.95)).thenReturn(2.9);
        when(pricingEngine.shouldBorrow(100_000L, 2.9)).thenReturn(true);
        when(quotaRepository.computeTotalSurplusTokens()).thenReturn(0L); // empty pool

        MarketIncentiveService.MarketApproval result = service.process("cs-dept", 100_000L);

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getDenyReason()).contains("empty");

        verifyNoInteractions(auctionSolver, ledgerService);
    }

    // ---- Rate-too-high path ---------------------------------------------

    @Test
    @DisplayName("Deficit + shouldBorrow=false → denied before auction due to high exchange rate")
    void process_rateTooHigh_deniedBeforeAuction() {
        when(quotaManager.checkAndReserve("bio-dept", 50_000L))
                .thenReturn(QuotaCheckResult.deficit(0L, 50_000L, 1.0));
        when(pricingEngine.computeExchangeRate(1.0)).thenReturn(3.0);
        when(pricingEngine.shouldBorrow(50_000L, 3.0)).thenReturn(false); // rate rejection

        MarketIncentiveService.MarketApproval result = service.process("bio-dept", 50_000L);

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getDenyReason()).isNotNull().isNotBlank();

        verifyNoInteractions(quotaRepository, auctionSolver, ledgerService);
    }

    // ---- Field propagation checks ---------------------------------------

    @Test
    @DisplayName("tenantId and systemUtilization are propagated to denial output")
    void process_denial_tenantIdAndUtilizationPropagated() {
        when(quotaManager.checkAndReserve("eng-dept", 10_000L))
                .thenReturn(QuotaCheckResult.deficit(0L, 10_000L, 0.88));
        when(pricingEngine.computeExchangeRate(0.88)).thenReturn(2.6);
        when(pricingEngine.shouldBorrow(10_000L, 2.6)).thenReturn(true);
        when(quotaRepository.computeTotalSurplusTokens()).thenReturn(0L);

        MarketIncentiveService.MarketApproval result = service.process("eng-dept", 10_000L);

        assertThat(result.getTenantId()).isEqualTo("eng-dept");
        assertThat(result.getSystemUtilization()).isCloseTo(0.88, within(0.001));
    }
}
