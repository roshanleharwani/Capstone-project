package com.nexus.market.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TenantQuotaManager}.
 *
 * <p>Uses a real H2 in-memory database to verify JPA mappings, custom queries,
 * and service logic integration without mocks. Tests cover:
 * <ul>
 *   <li>Pass-through when tenant has sufficient quota</li>
 *   <li>Deficit result when quota is exhausted</li>
 *   <li>Token reservation decrement</li>
 *   <li>Auto-provisioning of new tenants</li>
 *   <li>Quota window auto-reset when {@code resetAt} has passed</li>
 *   <li>Release reservation path</li>
 *   <li>Apply borrowed tokens path</li>
 * </ul>
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(TenantQuotaManager.class)
@DisplayName("TenantQuotaManager Integration Tests")
class TenantQuotaManagerTest {

    @Autowired
    private TenantQuotaRepository quotaRepository;

    @Autowired
    private TenantQuotaManager quotaManager;

    @BeforeEach
    void setUp() {
        quotaRepository.deleteAll();
        ReflectionTestUtils.setField(quotaManager, "defaultDailyLimit", 1_000_000L);
    }

    // ---- checkAndReserve: sufficient path --------------------------------

    @Test
    @DisplayName("Given sufficient quota, returns SUFFICIENT result and decrements usedTokens")
    void checkAndReserve_sufficientQuota_returnsSufficientResult() {
        // Arrange
        TenantQuota quota = tenantQuota("cs-dept", 1_000_000L, 200_000L);
        quotaRepository.save(quota);

        // Act
        QuotaCheckResult result = quotaManager.checkAndReserve("cs-dept", 100_000L);

        // Assert
        assertThat(result.isSufficient()).isTrue();
        assertThat(result.getTokensNeeded()).isEqualTo(100_000L);
        assertThat(result.getDeficitTokens()).isEqualTo(0L);
        
        // Verify token was reserved (usedTokens saved as 300_000)
        TenantQuota saved = quotaRepository.findByTenantId("cs-dept").orElseThrow();
        assertThat(saved.getUsedTokens()).isEqualTo(300_000L);
    }

    // ---- checkAndReserve: deficit path -----------------------------------

    @Test
    @DisplayName("Given exhausted quota, returns DEFICIT result without saving")
    void checkAndReserve_exhaustedQuota_returnsDeficitResult() {
        // Arrange — tenant has used all tokens
        TenantQuota quota = tenantQuota("cs-dept", 1_000_000L, 1_000_000L);
        quotaRepository.save(quota);

        // Act
        QuotaCheckResult result = quotaManager.checkAndReserve("cs-dept", 50_000L);

        // Assert
        assertThat(result.isSufficient()).isFalse();
        assertThat(result.getDeficitTokens()).isEqualTo(50_000L);
        assertThat(result.getRemainingTokens()).isEqualTo(0L);
        
        // No tokens decremented on deficit
        TenantQuota saved = quotaRepository.findByTenantId("cs-dept").orElseThrow();
        assertThat(saved.getUsedTokens()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("Given partial quota, deficit equals exact shortfall")
    void checkAndReserve_partialQuota_deficitIsShortfall() {
        // Arrange — tenant has 30k remaining, needs 50k
        TenantQuota quota = tenantQuota("bio-dept", 100_000L, 70_000L);
        quotaRepository.save(quota);

        // Act
        QuotaCheckResult result = quotaManager.checkAndReserve("bio-dept", 50_000L);

        // Assert
        assertThat(result.isSufficient()).isFalse();
        assertThat(result.getRemainingTokens()).isEqualTo(30_000L);
        assertThat(result.getDeficitTokens()).isEqualTo(20_000L); // 50k - 30k
    }

    // ---- Auto-provisioning -----------------------------------------------

    @Test
    @DisplayName("Unknown tenant is auto-provisioned with default daily limit")
    void checkAndReserve_unknownTenant_autoProvisions() {
        // Act
        QuotaCheckResult result = quotaManager.checkAndReserve("new-dept", 10_000L);

        // Assert
        assertThat(result.isSufficient()).isTrue();
        
        // Verify auto-provisioned in DB
        TenantQuota saved = quotaRepository.findByTenantId("new-dept").orElseThrow();
        assertThat(saved.getDailyLimit()).isEqualTo(1_000_000L);
        assertThat(saved.getUsedTokens()).isEqualTo(10_000L);
    }

    // ---- Quota window auto-reset -----------------------------------------

    @Test
    @DisplayName("Quota window auto-resets when resetAt is in the past")
    void checkAndReserve_expiredWindow_resetsUsedTokens() {
        // Arrange — quota is "full" but reset time is in the past
        TenantQuota quota = tenantQuota("admin-dept", 1_000_000L, 999_999L);
        quota.setResetAt(Instant.now().minus(1, ChronoUnit.DAYS)); // expired
        quotaRepository.save(quota);

        // Act — after reset, tenant should have 1M remaining
        QuotaCheckResult result = quotaManager.checkAndReserve("admin-dept", 50_000L);

        // Assert — should be sufficient after reset
        assertThat(result.isSufficient()).isTrue();
        
        TenantQuota saved = quotaRepository.findByTenantId("admin-dept").orElseThrow();
        assertThat(saved.getUsedTokens()).isEqualTo(50_000L);
    }

    // ---- releaseReservation ----------------------------------------------

    @Test
    @DisplayName("releaseReservation decrements usedTokens by the returned amount")
    void releaseReservation_decrementsUsedTokens() {
        // Arrange
        TenantQuota quota = tenantQuota("cs-dept", 1_000_000L, 500_000L);
        quotaRepository.save(quota);

        // Act
        quotaManager.releaseReservation("cs-dept", 100_000L);

        // Assert — usedTokens goes from 500k → 400k
        TenantQuota saved = quotaRepository.findByTenantId("cs-dept").orElseThrow();
        assertThat(saved.getUsedTokens()).isEqualTo(400_000L);
    }

    @Test
    @DisplayName("releaseReservation on unknown tenant is a no-op")
    void releaseReservation_unknownTenant_noOp() {
        quotaManager.releaseReservation("ghost", 10_000L);
        assertThat(quotaRepository.findByTenantId("ghost")).isEmpty();
    }

    // ---- applyBorrowedTokens --------------------------------------------

    @Test
    @DisplayName("applyBorrowedTokens increases dailyLimit by borrowed amount")
    void applyBorrowedTokens_increasesDailyLimit() {
        // Arrange
        TenantQuota quota = tenantQuota("cs-dept", 1_000_000L, 1_000_000L);
        quotaRepository.save(quota);

        // Act
        quotaManager.applyBorrowedTokens("cs-dept", 200_000L);

        // Assert — dailyLimit goes from 1M → 1.2M
        TenantQuota saved = quotaRepository.findByTenantId("cs-dept").orElseThrow();
        assertThat(saved.getDailyLimit()).isEqualTo(1_200_000L);
    }

    // ---- Helpers ---------------------------------------------------------

    private TenantQuota tenantQuota(String tenantId, long limit, long used) {
        Instant resetAt = Instant.now().plus(12, ChronoUnit.HOURS);
        return new TenantQuota(tenantId, limit, used, 0.0, resetAt);
    }
}
