package com.nexus.market.ledger;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a single entry in the immutable double-entry credit ledger.
 *
 * <p>The ledger records every token-borrowing transaction as a pair of entries:
 * <ul>
 *   <li>A {@code DEBIT} entry on the <em>borrower</em>'s account (credits owed)</li>
 *   <li>A {@code CREDIT} entry on the <em>lender</em>'s account (credits earned)</li>
 * </ul>
 *
 * <p><b>Immutability:</b> Entries are INSERT-only. The {@code transactionHash}
 * (SHA-256 of key fields + previous entry hash) chains entries in order, making
 * tampering cryptographically detectable — similar to a hash-linked audit log.
 *
 * <p><b>Row-Level Security:</b> In production PostgreSQL, an RLS policy ensures
 * each tenant's service account can only read its own ledger rows:
 * <pre>
 *   CREATE TABLE ledger_entries (
 *       id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
 *       tenant_id        VARCHAR(128) NOT NULL,
 *       counterparty_id  VARCHAR(128),
 *       entry_type       VARCHAR(16)  NOT NULL CHECK (entry_type IN ('DEBIT','CREDIT')),
 *       amount           NUMERIC(18,4) NOT NULL,
 *       description      TEXT,
 *       transaction_hash VARCHAR(64)  NOT NULL,
 *       timestamp        TIMESTAMPTZ  NOT NULL DEFAULT now()
 *   );
 *   ALTER TABLE ledger_entries ENABLE ROW LEVEL SECURITY;
 * </pre>
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    /** Discriminator for double-entry bookkeeping. */
    public enum EntryType { DEBIT, CREDIT }

    /** Unique transaction UUID. */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    /** Tenant whose ledger account this entry is on. */
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    /**
     * The counterparty in this transaction.
     * For DEBIT on borrower: this is the lender's tenantId.
     * For CREDIT on lender: this is the borrower's tenantId.
     */
    @Column(name = "counterparty_id", length = 128)
    private String counterpartyId;

    /** Whether this row records a DEBIT or CREDIT on {@code tenantId}'s account. */
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 16)
    private EntryType entryType;

    /** The credit amount (positive value); semantics determined by {@code entryType}. */
    @Column(name = "amount", nullable = false)
    private double amount;

    /** Human-readable description of the transaction. */
    @Column(name = "description")
    private String description;

    /**
     * SHA-256 hash of: {@code id + tenantId + entryType + amount + previousHash}.
     * Forms a cryptographic chain for tamper-evidence detection.
     */
    @Column(name = "transaction_hash", nullable = false, length = 64, updatable = false)
    private String transactionHash;

    /** UTC timestamp when this entry was created. */
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    public LedgerEntry() {}

    public LedgerEntry(String tenantId, String counterpartyId, EntryType entryType,
                       double amount, String description, String transactionHash) {
        this.id              = UUID.randomUUID().toString();
        this.tenantId        = tenantId;
        this.counterpartyId  = counterpartyId;
        this.entryType       = entryType;
        this.amount          = amount;
        this.description     = description;
        this.transactionHash = transactionHash;
        this.timestamp       = Instant.now();
    }

    // ---- Getters ---------------------------------------------------------

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getCounterpartyId() { return counterpartyId; }
    public EntryType getEntryType() { return entryType; }
    public double getAmount() { return amount; }
    public String getDescription() { return description; }
    public String getTransactionHash() { return transactionHash; }
    public Instant getTimestamp() { return timestamp; }
}
