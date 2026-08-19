package com.nexus.market.auction;

/**
 * The outcome of the VCG auction for a single tenant bidder.
 *
 * <p>For winning bids, {@code cleared == true} and the tenant is charged
 * {@code creditsPaid} (calculated at the VCG second-price, not their own bid).
 * For losing bids, {@code cleared == false} and {@code tokensAllocated == 0}.
 */
public class AuctionResult {

    /** The tenant this result applies to. */
    private final String tenantId;

    /** Number of tokens actually allocated (may be < bid if pool ran short). */
    private final long tokensAllocated;

    /**
     * Credits charged for the allocated tokens, computed at the VCG second-price.
     * Formula: {@code (tokensAllocated / 1000.0) * secondPriceRate}
     */
    private final double creditsPaid;

    /** Whether this tenant won any allocation in the auction. */
    private final boolean cleared;

    public AuctionResult(String tenantId, long tokensAllocated, double creditsPaid, boolean cleared) {
        this.tenantId       = tenantId;
        this.tokensAllocated = tokensAllocated;
        this.creditsPaid    = creditsPaid;
        this.cleared        = cleared;
    }

    // ---- Factory helpers -------------------------------------------------

    /** Creates a winning auction result. */
    public static AuctionResult winner(String tenantId, long tokensAllocated, double creditsPaid) {
        return new AuctionResult(tenantId, tokensAllocated, creditsPaid, true);
    }

    /** Creates a losing (uncleared) auction result. */
    public static AuctionResult loser(String tenantId) {
        return new AuctionResult(tenantId, 0L, 0.0, false);
    }

    // ---- Getters ---------------------------------------------------------

    public String getTenantId() { return tenantId; }
    public long getTokensAllocated() { return tokensAllocated; }
    public double getCreditsPaid() { return creditsPaid; }
    public boolean isCleared() { return cleared; }

    @Override
    public String toString() {
        return String.format("AuctionResult{tenant='%s', tokens=%d, credits=%.4f, cleared=%b}",
                tenantId, tokensAllocated, creditsPaid, cleared);
    }
}
