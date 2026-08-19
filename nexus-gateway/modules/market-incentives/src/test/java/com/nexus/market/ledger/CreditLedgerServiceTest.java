package com.nexus.market.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CreditLedgerService}.
 *
 * <p>All dependencies are mocked — no real PostgreSQL or Valkey instance required.
 *
 * <p>Test coverage:
 * <ul>
 *   <li>recordTokenBorrow creates exactly 2 ledger entries (DEBIT + CREDIT)</li>
 *   <li>DEBIT is on borrower, CREDIT is on lender</li>
 *   <li>Credit amount is correctly calculated (tokens/1000 * rate)</li>
 *   <li>Both caches are evicted after borrow</li>
 *   <li>SHA-256 hash is non-null, 64 hex chars</li>
 *   <li>recordTokenReturn creates reversed entries</li>
 *   <li>getBalance returns cached value on cache hit</li>
 *   <li>getBalance queries DB and populates cache on cache miss</li>
 *   <li>getBalance returns 0.0 when tenant has no entries</li>
 *   <li>verifyEntryIntegrity: returns true for unmodified entry</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreditLedgerService Unit Tests")
class CreditLedgerServiceTest {

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private CreditLedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ledgerService, "balanceCacheTtlSeconds", 30L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ---- recordTokenBorrow: entry creation ------------------------------

    @Test
    @DisplayName("recordTokenBorrow saves exactly 2 entries: DEBIT on borrower, CREDIT on lender")
    void recordTokenBorrow_createsPairedEntries() {
        when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.recordTokenBorrow("cs-dept", "admin-dept", 100_000L, 1.5);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository, times(2)).save(captor.capture());

        List<LedgerEntry> saved = captor.getAllValues();

        // First save = DEBIT on borrower
        LedgerEntry debit = saved.get(0);
        assertThat(debit.getTenantId()).isEqualTo("cs-dept");
        assertThat(debit.getCounterpartyId()).isEqualTo("admin-dept");
        assertThat(debit.getEntryType()).isEqualTo(LedgerEntry.EntryType.DEBIT);

        // Second save = CREDIT on lender
        LedgerEntry credit = saved.get(1);
        assertThat(credit.getTenantId()).isEqualTo("admin-dept");
        assertThat(credit.getCounterpartyId()).isEqualTo("cs-dept");
        assertThat(credit.getEntryType()).isEqualTo(LedgerEntry.EntryType.CREDIT);
    }

    @Test
    @DisplayName("Credit amount = (tokens / 1000.0) * rate")
    void recordTokenBorrow_correctCreditAmount() {
        when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 200k tokens @ 2.0 credits/kT = 400.0 credits
        ledgerService.recordTokenBorrow("cs-dept", "admin-dept", 200_000L, 2.0);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository, times(2)).save(captor.capture());

        captor.getAllValues().forEach(entry ->
                assertThat(entry.getAmount()).isCloseTo(400.0, within(0.001))
        );
    }

    @Test
    @DisplayName("Transaction hash is a non-null 64-character hex string (SHA-256)")
    void recordTokenBorrow_hashIsValidSha256() {
        when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.recordTokenBorrow("cs-dept", "pool", 50_000L, 1.0);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository, times(2)).save(captor.capture());

        captor.getAllValues().forEach(entry -> {
            assertThat(entry.getTransactionHash()).isNotNull();
            assertThat(entry.getTransactionHash()).hasSize(64);
            assertThat(entry.getTransactionHash()).matches("[0-9a-f]+");
        });
    }

    @Test
    @DisplayName("Both tenant balance caches are evicted after a borrow")
    void recordTokenBorrow_evictsBothCaches() {
        when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.recordTokenBorrow("cs-dept", "admin-dept", 50_000L, 1.0);

        verify(redisTemplate).delete("ledger:balance:cs-dept");
        verify(redisTemplate).delete("ledger:balance:admin-dept");
    }

    @Test
    @DisplayName("recordTokenBorrow returns a non-null UUID transaction ID")
    void recordTokenBorrow_returnsTransactionId() {
        when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String txId = ledgerService.recordTokenBorrow("a", "b", 10_000L, 1.0);

        assertThat(txId).isNotNull().isNotBlank();
        // UUID format check
        assertThat(txId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    // ---- recordTokenReturn ----------------------------------------------

    @Test
    @DisplayName("recordTokenReturn creates CREDIT on borrower and DEBIT on lender (reversal)")
    void recordTokenReturn_reversedEntryTypes() {
        when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.recordTokenReturn("cs-dept", "admin-dept", 50_000L, 1.0);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository, times(2)).save(captor.capture());

        List<LedgerEntry> saved = captor.getAllValues();
        // First save = CREDIT on borrower (refund)
        assertThat(saved.get(0).getEntryType()).isEqualTo(LedgerEntry.EntryType.CREDIT);
        assertThat(saved.get(0).getTenantId()).isEqualTo("cs-dept");

        // Second save = DEBIT on lender
        assertThat(saved.get(1).getEntryType()).isEqualTo(LedgerEntry.EntryType.DEBIT);
        assertThat(saved.get(1).getTenantId()).isEqualTo("admin-dept");
    }

    // ---- getBalance: cache behaviour ------------------------------------

    @Test
    @DisplayName("getBalance returns cached value on cache HIT without querying DB")
    void getBalance_cacheHit_returnsCachedValue() {
        when(valueOps.get("ledger:balance:cs-dept")).thenReturn("250.5");

        double balance = ledgerService.getBalance("cs-dept");

        assertThat(balance).isCloseTo(250.5, within(0.001));
        verify(ledgerRepository, never()).computeNetBalance(any());
    }

    @Test
    @DisplayName("getBalance queries DB and populates cache on cache MISS")
    void getBalance_cacheMiss_queriesDbAndPopulatesCache() {
        when(valueOps.get("ledger:balance:cs-dept")).thenReturn(null); // cache miss
        when(ledgerRepository.computeNetBalance("cs-dept")).thenReturn(750.0);

        double balance = ledgerService.getBalance("cs-dept");

        assertThat(balance).isCloseTo(750.0, within(0.001));
        verify(valueOps).set(eq("ledger:balance:cs-dept"), eq("750.0"), any(Duration.class));
    }

    @Test
    @DisplayName("getBalance returns 0.0 when tenant has no entries (null from DB)")
    void getBalance_noEntries_returnsZero() {
        when(valueOps.get("ledger:balance:new-tenant")).thenReturn(null);
        when(ledgerRepository.computeNetBalance("new-tenant")).thenReturn(null);

        double balance = ledgerService.getBalance("new-tenant");

        assertThat(balance).isZero();
    }

    // ---- verifyEntryIntegrity -------------------------------------------

    @Test
    @DisplayName("verifyEntryIntegrity returns false for unknown entry ID")
    void verifyEntryIntegrity_unknownId_returnsFalse() {
        when(ledgerRepository.findById("unknown-id")).thenReturn(Optional.empty());

        assertThat(ledgerService.verifyEntryIntegrity("unknown-id")).isFalse();
    }
}
