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
 * <p>Winners pay the bid of the first unsuccessful bidder (or {@code minBid} when
 * every eligible bid clears). Partial fills are winners; the next unpaid bid sets price.
 */
@Service
public class VcgAuctionSolver {

    private static final Logger log = LoggerFactory.getLogger(VcgAuctionSolver.class);

    @Value("${market.auction.min-bid:0.1}")
    private double minBid;

    public List<AuctionResult> solve(List<TenantBid> bids, long availablePoolTokens) {
        if (bids == null || bids.isEmpty()) {
            log.info("[VCG Auction] No bids received — pool of {} tokens remains unallocated.",
                    availablePoolTokens);
            return List.of();
        }

        List<TenantBid> eligible = bids.stream()
                .filter(b -> b.getCreditBidPerKTokens() >= minBid)
                .sorted(Comparator.comparingDouble(TenantBid::getCreditBidPerKTokens).reversed()
                        .thenComparing(TenantBid::getTenantId))
                .toList();

        log.info("[VCG Auction] Starting auction: {} eligible bids, pool={} tokens",
                eligible.size(), availablePoolTokens);

        List<AuctionResult> provisional = new ArrayList<>();
        long remainingPool = Math.max(0L, availablePoolTokens);
        int firstUnsuccessfulIndex = eligible.size();

        for (int i = 0; i < eligible.size(); i++) {
            TenantBid bid = eligible.get(i);
            if (remainingPool <= 0) {
                firstUnsuccessfulIndex = Math.min(firstUnsuccessfulIndex, i);
                provisional.add(AuctionResult.loser(bid.getTenantId()));
                continue;
            }

            long allocated = Math.min(bid.getTokensNeeded(), remainingPool);
            remainingPool -= allocated;

            if (allocated <= 0) {
                firstUnsuccessfulIndex = Math.min(firstUnsuccessfulIndex, i);
                provisional.add(AuctionResult.loser(bid.getTenantId()));
            } else {
                provisional.add(new AuctionResult(bid.getTenantId(), allocated, 0.0, true));
                // After a partial fill the pool is empty; later bids are unsuccessful.
                if (allocated < bid.getTokensNeeded()) {
                    firstUnsuccessfulIndex = Math.min(firstUnsuccessfulIndex, i + 1);
                }
            }
        }

        double secondPrice = (firstUnsuccessfulIndex < eligible.size())
                ? eligible.get(firstUnsuccessfulIndex).getCreditBidPerKTokens()
                : minBid;

        log.info("[VCG Auction] Second-price rate: {} credits/kTokens", secondPrice);

        List<AuctionResult> finalResults = new ArrayList<>();
        for (AuctionResult raw : provisional) {
            if (raw.isCleared()) {
                double credits = (raw.getTokensAllocated() / 1000.0) * secondPrice;
                finalResults.add(AuctionResult.winner(raw.getTenantId(), raw.getTokensAllocated(), credits));
                log.info("[VCG Auction] WINNER tenant={} tokens={} credits={}",
                        raw.getTenantId(), raw.getTokensAllocated(), credits);
            } else {
                finalResults.add(AuctionResult.loser(raw.getTenantId()));
            }
        }

        bids.stream()
                .filter(b -> b.getCreditBidPerKTokens() < minBid)
                .forEach(b -> finalResults.add(AuctionResult.loser(b.getTenantId())));

        return finalResults;
    }

    double getMinBid() { return minBid; }
}
