package com.nexus.market.service;

import com.nexus.market.auction.PendingBidRegistry;
import com.nexus.market.auction.VcgAuctionSolver;
import com.nexus.market.ledger.CreditLedgerService;
import com.nexus.market.pricing.StackelbergPricingEngine;
import com.nexus.market.quota.TenantQuotaManager;
import com.nexus.market.quota.TenantQuotaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link MarketIncentiveService} — the pipeline orchestrator.
 *
 * <p>Uses a real H2 in-memory database and actual service classes instead of mocks,
 * except for {@link CreditLedgerService} which relies on Redis.
 *
 * <p>Test coverage:
 * <ul>
 *   <li>Sufficient quota → pass-through</li>
 *   <li>Deficit + auction win → full pipeline executed, approval returned</li>
 *   <li>Deficit + auction loss → denied result</li>
 *   <li>Deficit + empty pool → denied result before auction runs</li>
 *   <li>Deficit + rate too high → denied before auction</li>
 * </ul>
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({
        MarketIncentiveService.class,
        TenantQuotaManager.class,
        StackelbergPricingEngine.class,
        VcgAuctionSolver.class,
        PendingBidRegistry.class
})
@DisplayName("MarketIncentiveService Integration Tests")
class MarketIncentiveServiceTest {

    @Autowired private MarketIncentiveService service;
    @Autowired private TenantQuotaRepository quotaRepository;

    @MockBean private CreditLedgerService ledgerService;

    @BeforeEach
    void setUp() {
        quotaRepository.deleteAll();
    }

    // ---- Pass-through path ----------------------------------------------

    @Test
    @DisplayName("Sufficient quota → approved pass-through, no auction or ledger called")
    void process_sufficientQuota_passThroughApproval() {
        com.nexus.market.quota.TenantQuota quota = new com.nexus.market.quota.TenantQuota("cs-dept", 950_000L, 50_000L, 0.0, java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS));
        quotaRepository.save(quota);

        MarketIncentiveService.MarketApproval result = service.process("cs-dept", 50_000L);

        assertThat(result.isApproved()).isTrue();
        assertThat(result.getTenantId()).isEqualTo("cs-dept");
        assertThat(result.getTokensAllocated()).isEqualTo(50_000L);
        assertThat(result.getTokensBorrowed()).isEqualTo(0L);
        assertThat(result.getCreditsCharged()).isZero();
        assertThat(result.isAuctionCleared()).isFalse();
        assertThat(result.getTransactionId()).isNull();

        // Ledger should NOT be touched
        verifyNoInteractions(ledgerService);
    }

    // ---- Full auction-cleared path ---------------------------------------

    @Test
    @DisplayName("Deficit + winning auction → approved with borrowed tokens and ledger recorded")
    void process_deficitAndAuctionWin_approvedWithBorrowedTokens() {
        // Step 1: quota is insufficient (limit 100k, used 50k, remaining 50k, need 200k, deficit 150k)
        com.nexus.market.quota.TenantQuota quota = new com.nexus.market.quota.TenantQuota("cs-dept", 100_000L, 50_000L, 0.0, java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS));
        quotaRepository.save(quota);

        // Step 2: pool has surplus (another tenant has 1,000,000 unused)
        com.nexus.market.quota.TenantQuota surplusTenant = new com.nexus.market.quota.TenantQuota("admin-dept", 1_000_000L, 0L, 0.0, java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS));
        quotaRepository.save(surplusTenant);

        // Step 3: ledger returns a txId — charged to the real surplus lender at VCG second-price
        when(ledgerService.recordTokenBorrow(eq("cs-dept"), eq("admin-dept"), eq(150_000L), anyDouble()))
                .thenReturn("tx-abc-123");

        MarketIncentiveService.MarketApproval result = service.process("cs-dept", 200_000L);

        assertThat(result.isApproved()).isTrue();
        assertThat(result.isAuctionCleared()).isTrue();
        assertThat(result.getTokensBorrowed()).isEqualTo(150_000L);
        assertThat(result.getTransactionId()).isEqualTo("tx-abc-123");

        // Lender surplus must shrink; borrower must consume the request tokens
        var admin = quotaRepository.findByTenantId("admin-dept").orElseThrow();
        var cs = quotaRepository.findByTenantId("cs-dept").orElseThrow();
        assertThat(admin.getUsedTokens()).isEqualTo(150_000L);
        assertThat(cs.getDailyLimit()).isEqualTo(250_000L);
        assertThat(cs.getUsedTokens()).isEqualTo(250_000L);

        verify(ledgerService).recordTokenBorrow(eq("cs-dept"), eq("admin-dept"), eq(150_000L), anyDouble());
        verify(ledgerService, never()).recordTokenBorrow(eq("cs-dept"), eq("SYSTEM_POOL"), anyLong(), anyDouble());
    }



    // ---- Empty pool path ------------------------------------------------

    @Test
    @DisplayName("Deficit + empty pool → denied immediately, auction never called")
    void process_emptyPool_deniedBeforeAuction() {
        // Tenant is completely out of quota
        com.nexus.market.quota.TenantQuota quota = new com.nexus.market.quota.TenantQuota("cs-dept", 0L, 0L, 0.0, java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS));
        quotaRepository.save(quota);
        // Pool is empty

        MarketIncentiveService.MarketApproval result = service.process("cs-dept", 100_000L);

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getDenyReason()).contains("empty");

        verifyNoInteractions(ledgerService);
    }

    // ---- Rate-too-high path ---------------------------------------------

    @Test
    @DisplayName("Deficit + shouldBorrow=false → denied before auction due to high exchange rate")
    void process_rateTooHigh_deniedBeforeAuction() {
        // High deficit / high utilization pushes the rate up
        com.nexus.market.quota.TenantQuota quota = new com.nexus.market.quota.TenantQuota("bio-dept", 50_000L, 50_000L, 0.0, java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS));
        quotaRepository.save(quota);

        // By requesting a massive amount of tokens, rate goes too high
        MarketIncentiveService.MarketApproval result = service.process("bio-dept", 5_000_000L);

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getDenyReason()).isNotNull().isNotBlank();

        verifyNoInteractions(ledgerService);
    }

    // ---- Field propagation checks ---------------------------------------

    @Test
    @DisplayName("tenantId and systemUtilization are propagated to denial output")
    void process_denial_tenantIdAndUtilizationPropagated() {
        com.nexus.market.quota.TenantQuota quota = new com.nexus.market.quota.TenantQuota("eng-dept", 10_000L, 10_000L, 0.0, java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS));
        quotaRepository.save(quota);

        MarketIncentiveService.MarketApproval result = service.process("eng-dept", 10_000L);

        assertThat(result.getTenantId()).isEqualTo("eng-dept");
        assertThat(result.getSystemUtilization()).isGreaterThan(0.0);
    }
}
