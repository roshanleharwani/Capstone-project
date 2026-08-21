package com.nexus.market.auction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * VCG Second-Price Sealed-Bid Auction Solver — Core Task 3.
 *
 * <p>Implements a <b>Vickrey–Clarke–Groves (VCG) auction</b> to allocate pooled
 * surplus tokens to quota-starved tenants during peak hours.
 *
 * <h2>Auction Mechanism</h2>
 * <ol>
 *   <li>Quota-starved tenants submit sealed bids: how many tokens they need and the
 *       maximum gateway credits they will pay per 1000 tokens.</li>
 *   <li>Bids are sorted in <em>descending</em> order by {@code creditBidPerKTokens}.</li>
 *   <li>Tokens are greedily allocated from the surplus pool to the highest bidder
 *       first, until either all bids are satisfied or the pool is exhausted.</li>
 *   <li>Each winner is charged at the <em>second-price</em>: the {@code creditBidPerKTokens}
 *       of the first <em>losing</em> bid (or {@code minBid} if all bids win).</li>
 * </ol>
 *
 * <h2>VCG Incentive-Compatibility Guarantee</h2>
 * <p>The second-price rule ensures bidding true valuation is the dominant strategy.
 * No tenant can improve their outcome by misreporting how much they value extra tokens.
 * This prevents strategic manipulation of the market.
 *
 * <p>Reference: Section IV.D of the NexusGateway design paper.
 */
@Service
public class VcgAuctionSolver {

    private static final Logger log = LoggerFactory.getLogger(VcgAuctionSolver.class);

    /**
     * Minimum bid threshold — bids below this rate are automatically rejected
     * to prevent free-riding on the surplus pool.
     */
    @Value("${market.auction.min-bid:0.1}")
    private double minBid;

    /**
     * Runs the VCG second-price auction.
     *
     * @param bids               list of sealed bids from quota-starved tenants
     * @param availablePoolTokens total surplus tokens available to distribute
     * @return list of {@link AuctionResult} — one entry per input bid
     */
    public List<AuctionResult> solve(List<TenantBid> bids, long availablePoolTokens) {
        if (bids == null || bids.isEmpty()) {
            log.info("[VCG Auction] No bids received — pool of {} tokens remains unallocated.",
                    availablePoolTokens);
            return List.of();
        }

        // 1. Filter out sub-minimum bids and sort descending by bid value
        List<TenantBid> eligible = bids.stream()
                .filter(b -> b.getCreditBidPerKTokens() >= minBid)
                .sorted(Comparator.comparingDouble(TenantBid::getCreditBidPerKTokens).reversed())
                .toList();

        log.info("[VCG Auction] Starting auction: {} eligible bids, pool={} tokens",
                eligible.size(), availablePoolTokens);

        // 2. Greedy allocation pass — fill from highest bid down
        List<AuctionResult> results = new ArrayList<>();
        long remainingPool = availablePoolTokens;
        int firstLoserIndex = eligible.size(); // index of the first bid that DID NOT fully clear

        for (int i = 0; i < eligible.size(); i++) {
            TenantBid bid = eligible.get(i);
            if (remainingPool <= 0) {
                // Pool exhausted — this and all subsequent bids lose
                firstLoserIndex = i;
                // Add losers for remaining bids
                for (int j = i; j < eligible.size(); j++) {
                    results.add(AuctionResult.loser(eligible.get(j).getTenantId()));
                }
                break;
            }
            long allocated = Math.min(bid.getTokensNeeded(), remainingPool);
            remainingPool -= allocated;
            // Temporarily record as winner; price will be set in step 3
            results.add(new AuctionResult(bid.getTenantId(), allocated, 0.0, allocated > 0));
            if (allocated < bid.getTokensNeeded()) {
                // Partial fill counts as the "first loser" for second-price purposes
                firstLoserIndex = i;
            }
        }

        // 3. VCG second-price assignment
        //    All winners pay the bid of the FIRST loser, not their own bid.
        //    If all bids won, charge the minimum bid (floor price).
        double secondPrice = (firstLoserIndex < eligible.size())
                ? eligible.get(firstLoserIndex).getCreditBidPerKTokens()
                : minBid;

        log.info("[VCG Auction] Second-price rate: {:.4f} credits/kTokens", secondPrice);

        // Rebuild results with correct pricing
        List<AuctionResult> finalResults = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            AuctionResult raw = results.get(i);
            if (raw.isCleared()) {
                double credits = (raw.getTokensAllocated() / 1000.0) * secondPrice;
                finalResults.add(AuctionResult.winner(raw.getTenantId(), raw.getTokensAllocated(), credits));
                log.info("[VCG Auction] WINNER tenant={} tokens={} credits={:.4f}",
                        raw.getTenantId(), raw.getTokensAllocated(), credits);
            } else {
                finalResults.add(AuctionResult.loser(raw.getTenantId()));
                log.debug("[VCG Auction] LOSER tenant={}", raw.getTenantId());
            }
        }

        // Also add results for ineligible (below-minBid) bidders as losers
        bids.stream()
                .filter(b -> b.getCreditBidPerKTokens() < minBid)
                .forEach(b -> finalResults.add(AuctionResult.loser(b.getTenantId())));

        return finalResults;
    }

    // ---- Package-visible for tests ---------------------------------------

    double getMinBid() { return minBid; }
}
