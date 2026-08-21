package com.nexus.market.auction;

/**
 * Represents a single tenant's sealed bid in the VCG second-price token auction.
 *
 * <p>Tenants submit bids declaring:
 * <ul>
 *   <li>How many tokens they need</li>
 *   <li>Their valuation (maximum credits they are willing to pay per 1000 tokens)</li>
 * </ul>
 *
 * <p>Because VCG auction theory guarantees that bidding true valuation is the
 * dominant strategy (no benefit from under- or over-bidding), tenants should
 * declare their actual willingness-to-pay.
 *
 * <p>Reference: Section IV.D of the NexusGateway design paper.
 */
public class TenantBid {

    /** Tenant identifier (from X-Tenant-ID header). */
    private final String tenantId;

    /** Number of tokens the tenant wishes to borrow from the surplus pool. */
    private final long tokensNeeded;

    /**
     * The maximum number of gateway credits this tenant is willing to pay
     * per 1000 tokens allocated. Higher bids have priority allocation.
     */
    private final double creditBidPerKTokens;

    public TenantBid(String tenantId, long tokensNeeded, double creditBidPerKTokens) {
        if (tokensNeeded <= 0) throw new IllegalArgumentException("tokensNeeded must be > 0");
        if (creditBidPerKTokens < 0) throw new IllegalArgumentException("creditBid must be >= 0");
        this.tenantId            = tenantId;
        this.tokensNeeded        = tokensNeeded;
        this.creditBidPerKTokens = creditBidPerKTokens;
    }

    public String getTenantId() { return tenantId; }
    public long getTokensNeeded() { return tokensNeeded; }
    public double getCreditBidPerKTokens() { return creditBidPerKTokens; }

    @Override
    public String toString() {
        return String.format("TenantBid{tenant='%s', tokens=%d, bid=%.4f}", tenantId, tokensNeeded, creditBidPerKTokens);
    }
}
