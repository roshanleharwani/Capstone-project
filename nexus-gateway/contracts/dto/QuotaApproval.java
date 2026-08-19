package contracts.dto;

import java.util.Map;

/**
 * Shared contract DTO: Output of the market-incentives module (Member 3).
 *
 * <p>Produced by the NexusMarket Incentive Engine after running quota checks,
 * Stackelberg dynamic pricing, VCG auction allocation, and credit ledger recording.
 * Consumed by the context-compression module (Member 2) as its pipeline input.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code approved} — whether the request may proceed (own quota or borrowed tokens)</li>
 *   <li>{@code tokensAllocated} — total tokens authorized (own + borrowed)</li>
 *   <li>{@code tokensBorrowed} — tokens sourced from the surplus pool (0 if pass-through)</li>
 *   <li>{@code creditsCharged} — gateway credits charged at the VCG second-price</li>
 *   <li>{@code exchangeRate} — Stackelberg p_t rate (credits/kTokens) at time of request</li>
 *   <li>{@code auctionCleared} — true if a VCG auction was run and won</li>
 *   <li>{@code transactionId} — UUID linking paired ledger entries (null if pass-through)</li>
 *   <li>{@code metadata} — audit metadata (module version, pipeline stage)</li>
 * </ul>
 */
public class QuotaApproval {

    private String sessionId;
    private String tenantId;

    /** Whether the tenant is approved to proceed with the LLM request. */
    private boolean approved;

    /** Total tokens authorized for this request (own quota + borrowed). */
    private long tokensAllocated;

    /**
     * Tokens borrowed from the surplus pool via VCG auction.
     * Zero for pass-through requests that had sufficient own quota.
     */
    private long tokensBorrowed;

    /**
     * Gateway credits charged for the borrowed tokens at the VCG second-price.
     * Zero for pass-through requests.
     */
    private double creditsCharged;

    /**
     * The Stackelberg exchange rate (p_t) in credits per 1000 tokens at the
     * time this request was processed. Zero for pass-through requests.
     */
    private double exchangeRate;

    /**
     * True if a VCG auction was successfully conducted and this tenant won
     * an allocation from the surplus pool.
     */
    private boolean auctionCleared;

    /**
     * UUID linking the paired DEBIT/CREDIT entries in the immutable ledger.
     * Null for pass-through requests (no borrow occurred).
     */
    private String transactionId;

    /**
     * Supplementary audit metadata: module name, version, pipeline stage, etc.
     */
    private Map<String, String> metadata;

    public QuotaApproval() {}

    public QuotaApproval(String sessionId, String tenantId, boolean approved,
                         long tokensAllocated, long tokensBorrowed, double creditsCharged,
                         double exchangeRate, boolean auctionCleared,
                         String transactionId, Map<String, String> metadata) {
        this.sessionId      = sessionId;
        this.tenantId       = tenantId;
        this.approved       = approved;
        this.tokensAllocated = tokensAllocated;
        this.tokensBorrowed = tokensBorrowed;
        this.creditsCharged = creditsCharged;
        this.exchangeRate   = exchangeRate;
        this.auctionCleared = auctionCleared;
        this.transactionId  = transactionId;
        this.metadata       = metadata;
    }

    // ---- Getters & Setters -----------------------------------------------

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public long getTokensAllocated() { return tokensAllocated; }
    public void setTokensAllocated(long tokensAllocated) { this.tokensAllocated = tokensAllocated; }

    public long getTokensBorrowed() { return tokensBorrowed; }
    public void setTokensBorrowed(long tokensBorrowed) { this.tokensBorrowed = tokensBorrowed; }

    public double getCreditsCharged() { return creditsCharged; }
    public void setCreditsCharged(double creditsCharged) { this.creditsCharged = creditsCharged; }

    public double getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(double exchangeRate) { this.exchangeRate = exchangeRate; }

    public boolean isAuctionCleared() { return auctionCleared; }
    public void setAuctionCleared(boolean auctionCleared) { this.auctionCleared = auctionCleared; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}
