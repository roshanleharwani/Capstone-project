package com.nexus.market.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TenantQuotaManager}.
 *
 * <p>All dependencies (repository) are mocked via Mockito.
 * No Spring context is loaded. Tests cover:
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
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantQuotaManager Unit Tests")
class TenantQuotaManagerTest {

    @Mock
    private TenantQuotaRepository quotaRepository;

    @InjectMocks
    private TenantQuotaManager quotaManager;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(quotaManager, "defaultDailyLimit", 1_000_000L);
    }

    // ---- checkAndReserve: sufficient path --------------------------------

    @Test
    @DisplayName("Given sufficient quota, returns SUFFICIENT result and decrements usedTokens")
    void checkAndReserve_sufficientQuota_returnsSufficientResult() {
        // Arrange
        TenantQuota quota = tenantQuota("cs-dept", 1_000_000L, 200_000L);
        when(quotaRepository.findByTenantId("cs-dept")).thenReturn(Optional.of(quota));
        when(quotaRepository.save(any())).thenReturn(quota);
        when(quotaRepository.computeTotalSurplusTokens()).thenReturn(800_000L);
        when(quotaRepository.count()).thenReturn(1L);

        // Act
        QuotaCheckResult result = quotaManager.checkAndReserve("cs-dept", 100_000L);

        // Assert
        assertThat(result.isSufficient()).isTrue();
        assertThat(result.getTokensNeeded()).isEqualTo(100_000L);
        assertThat(result.getDeficitTokens()).isEqualTo(0L);
        // Verify token was reserved (usedTokens saved as 300_000)
        verify(quotaRepository).save(argThat(q -> q.getUsedTokens() == 300_000L));
    }

    // ---- checkAndReserve: deficit path -----------------------------------

    @Test
    @DisplayName("Given exhausted quota, returns DEFICIT result without saving")
    void checkAndReserve_exhaustedQuota_returnsDeficitResult() {
        // Arrange — tenant has used all tokens
        TenantQuota quota = tenantQuota("cs-dept", 1_000_000L, 1_000_000L);
        when(quotaRepository.findByTenantId("cs-dept")).thenReturn(Optional.of(quota));
        when(quotaRepository.computeTotalSurplusTokens()).thenReturn(0L);
        when(quotaRepository.count()).thenReturn(2L);

        // Act
        QuotaCheckResult result = quotaManager.checkAndReserve("cs-dept", 50_000L);

        // Assert
        assertThat(result.isSufficient()).isFalse();
        assertThat(result.getDeficitTokens()).isEqualTo(50_000L);
        assertThat(result.getRemainingTokens()).isEqualTo(0L);
        // No save should occur — tokens NOT decremented on deficit
        verify(quotaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Given partial quota, deficit equals exact shortfall")
    void checkAndReserve_partialQuota_deficitIsShortfall() {
        // Arrange — tenant has 30k remaining, needs 50k
        TenantQuota quota = tenantQuota("bio-dept", 100_000L, 70_000L);
        when(quotaRepository.findByTenantId("bio-dept")).thenReturn(Optional.of(quota));
        when(quotaRepository.computeTotalSurplusTokens()).thenReturn(30_000L);
        when(quotaRepository.count()).thenReturn(3L);

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
        // Arrange
        when(quotaRepository.findByTenantId("new-dept")).thenReturn(Optional.empty());
        when(quotaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(quotaRepository.computeTotalSurplusTokens()).thenReturn(900_000L);
        when(quotaRepository.count()).thenReturn(1L);

        // Act
        QuotaCheckResult result = quotaManager.checkAndReserve("new-dept", 10_000L);

        // Assert
        assertThat(result.isSufficient()).isTrue();
        // Verify auto-provision: save called twice (once for provisioning, once for reservation)
        verify(quotaRepository, times(2)).save(any(TenantQuota.class));
    }

    // ---- Quota window auto-reset -----------------------------------------

    @Test
    @DisplayName("Quota window auto-resets when resetAt is in the past")
    void checkAndReserve_expiredWindow_resetsUsedTokens() {
        // Arrange — quota is "full" but reset time is in the past
        TenantQuota quota = tenantQuota("admin-dept", 1_000_000L, 999_999L);
        quota.setResetAt(Instant.now().minus(1, ChronoUnit.DAYS)); // expired
        when(quotaRepository.findByTenantId("admin-dept")).thenReturn(Optional.of(quota));
        when(quotaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(quotaRepository.computeTotalSurplusTokens()).thenReturn(0L);
        when(quotaRepository.count()).thenReturn(1L);

        // Act — after reset, tenant should have 1M remaining
        QuotaCheckResult result = quotaManager.checkAndReserve("admin-dept", 50_000L);

        // Assert — should be sufficient after reset
        assertThat(result.isSufficient()).isTrue();
    }

    // ---- releaseReservation ----------------------------------------------

    @Test
    @DisplayName("releaseReservation decrements usedTokens by the returned amount")
    void releaseReservation_decrementsUsedTokens() {
        // Arrange
        TenantQuota quota = tenantQuota("cs-dept", 1_000_000L, 500_000L);
        when(quotaRepository.findByTenantId("cs-dept")).thenReturn(Optional.of(quota));
        when(quotaRepository.save(any())).thenReturn(quota);

        // Act
        quotaManager.releaseReservation("cs-dept", 100_000L);

        // Assert — usedTokens goes from 500k → 400k
        verify(quotaRepository).save(argThat(q -> q.getUsedTokens() == 400_000L));
    }

    @Test
    @DisplayName("releaseReservation on unknown tenant is a no-op")
    void releaseReservation_unknownTenant_noOp() {
        when(quotaRepository.findByTenantId("ghost")).thenReturn(Optional.empty());
        quotaManager.releaseReservation("ghost", 10_000L);
        verify(quotaRepository, never()).save(any());
    }

    // ---- applyBorrowedTokens --------------------------------------------

    @Test
    @DisplayName("applyBorrowedTokens increases dailyLimit by borrowed amount")
    void applyBorrowedTokens_increasesDailyLimit() {
        // Arrange
        TenantQuota quota = tenantQuota("cs-dept", 1_000_000L, 1_000_000L);
        when(quotaRepository.findByTenantId("cs-dept")).thenReturn(Optional.of(quota));
        when(quotaRepository.save(any())).thenReturn(quota);

        // Act
        quotaManager.applyBorrowedTokens("cs-dept", 200_000L);

        // Assert — dailyLimit goes from 1M → 1.2M
        verify(quotaRepository).save(argThat(q -> q.getDailyLimit() == 1_200_000L));
    }

    // ---- Helpers ---------------------------------------------------------

    private TenantQuota tenantQuota(String tenantId, long limit, long used) {
        Instant resetAt = Instant.now().plus(12, ChronoUnit.HOURS);
        return new TenantQuota(tenantId, limit, used, 0.0, resetAt);
    }
}
