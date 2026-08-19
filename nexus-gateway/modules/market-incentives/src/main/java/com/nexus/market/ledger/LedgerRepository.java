package com.nexus.market.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link LedgerEntry} records.
 *
 * <p>This repository is read-heavy for balance queries. All write paths go through
 * {@link CreditLedgerService} which handles hash chaining before calling
 * {@link #save(Object)}.
 *
 * <p>No {@code deleteBy*} or {@code update*} methods are exposed — the ledger
 * is INSERT-only by design.
 */
@Repository
public interface LedgerRepository extends JpaRepository<LedgerEntry, String> {

    /**
     * Retrieves all ledger entries for a tenant in reverse-chronological order.
     * Used for displaying transaction history and for computing running balances.
     *
     * @param tenantId the tenant whose history to retrieve
     * @return entries ordered by timestamp descending
     */
    List<LedgerEntry> findByTenantIdOrderByTimestampDesc(String tenantId);

    /**
     * Looks up a ledger entry by its SHA-256 hash for integrity verification.
     * Returns empty if the hash is not found (possible tampering indicator).
     *
     * @param hash the hex-encoded SHA-256 transaction hash to look up
     * @return the entry if found
     */
    Optional<LedgerEntry> findByTransactionHash(String hash);

    /**
     * Computes the net credit balance for a tenant.
     *
     * <p>Balance = SUM(CREDIT entries) - SUM(DEBIT entries)
     * A positive balance means the tenant has credits available to spend.
     *
     * @param tenantId the tenant to compute balance for
     * @return net credit balance; null if no entries exist (treated as 0.0 by callers)
     */
    @Query("""
            SELECT COALESCE(
              SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END),
              0.0
            )
            FROM LedgerEntry e
            WHERE e.tenantId = :tenantId
            """)
    Double computeNetBalance(@Param("tenantId") String tenantId);

    /**
     * Returns the most recently created ledger entry for a tenant.
     * Used by {@link CreditLedgerService} to obtain the previous hash
     * for chain continuation.
     *
     * @param tenantId the tenant to query
     * @return the newest entry, or empty if the tenant has no ledger history
     */
    Optional<LedgerEntry> findTopByTenantIdOrderByTimestampDesc(String tenantId);

    /**
     * Fetches all ledger entries that reference a specific transaction UUID as either
     * the primary entry or counterparty — useful for auditing a full borrow/lend pair.
     *
     * @param transactionId a UUID prefix that may appear in the {@code description} field
     * @return matching entries
     */
    @Query("SELECT e FROM LedgerEntry e WHERE e.description LIKE CONCAT('%', :txId, '%')")
    List<LedgerEntry> findByTransactionIdInDescription(@Param("txId") String transactionId);
}
