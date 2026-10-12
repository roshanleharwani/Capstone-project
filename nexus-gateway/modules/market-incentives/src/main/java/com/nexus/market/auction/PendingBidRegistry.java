package com.nexus.market.auction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sealed-bid registry so concurrent deficit tenants compete in one VCG round
 * instead of each request running a trivial single-bidder auction.
 */
@Service
public class PendingBidRegistry {

    private final ConcurrentHashMap<String, TimedBid> bids = new ConcurrentHashMap<>();

    @Value("${market.auction.bid-ttl-seconds:120}")
    private long bidTtlSeconds;

    public void submit(TenantBid bid) {
        bids.put(bid.getTenantId(), new TimedBid(bid, Instant.now()));
    }

    /** Snapshot of non-expired bids (including the just-submitted one). */
    public List<TenantBid> snapshotActive() {
        Instant cutoff = Instant.now().minusSeconds(Math.max(1L, bidTtlSeconds));
        List<TenantBid> active = new ArrayList<>();
        for (Map.Entry<String, TimedBid> entry : bids.entrySet()) {
            if (entry.getValue().submittedAt().isBefore(cutoff)) {
                bids.remove(entry.getKey(), entry.getValue());
                continue;
            }
            active.add(entry.getValue().bid());
        }
        return active;
    }

    public void clear(Set<String> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return;
        }
        tenantIds.forEach(bids::remove);
    }

    public void clear(String tenantId) {
        bids.remove(tenantId);
    }

    private record TimedBid(TenantBid bid, Instant submittedAt) {}
}
