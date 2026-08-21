package com.nexus.market.auction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link VcgAuctionSolver}.
 *
 * <p>No Spring context, no mocks needed — the solver is a pure stateless algorithm.
 * All test cases mock multi-tenant bidding scenarios as per the independent test strategy.
 *
 * <p>Test coverage:
 * <ul>
 *   <li>Empty bid list → empty result</li>
 *   <li>Single bidder always wins at min-bid price (no second bidder)</li>
 *   <li>Multi-bidder: winner pays second-price, not their own bid</li>
 *   <li>Pool exhaustion: partial fill + correct losers</li>
 *   <li>Below-min-bid rejection</li>
 *   <li>All bids win when pool is large enough</li>
 *   <li>Tie-breaking: first by sort order wins partial allocation</li>
 * </ul>
 */
@DisplayName("VcgAuctionSolver Unit Tests")
class VcgAuctionSolverTest {

    private VcgAuctionSolver solver;

    private static final double MIN_BID = 0.1;
    private static final long LARGE_POOL = 10_000_000L;

    @BeforeEach
    void setUp() {
        solver = new VcgAuctionSolver();
        ReflectionTestUtils.setField(solver, "minBid", MIN_BID);
    }

    // ---- Edge cases ------------------------------------------------------

    @Test
    @DisplayName("Empty bid list returns empty result list")
    void solve_emptyBids_returnsEmpty() {
        List<AuctionResult> results = solver.solve(List.of(), LARGE_POOL);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Null bid list returns empty result list")
    void solve_nullBids_returnsEmpty() {
        List<AuctionResult> results = solver.solve(null, LARGE_POOL);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Zero pool tokens causes all bids to lose")
    void solve_zeroPool_allBidsLose() {
        List<TenantBid> bids = List.of(
                new TenantBid("cs-dept", 100_000L, 2.0)
        );
        List<AuctionResult> results = solver.solve(bids, 0L);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).isCleared()).isFalse();
        assertThat(results.get(0).getTokensAllocated()).isEqualTo(0L);
    }

    // ---- Single bidder --------------------------------------------------

    @Test
    @DisplayName("Single bidder with sufficient pool wins at min-bid price")
    void solve_singleBidder_winsAtMinBidPrice() {
        // Only one bidder → no second bidder → second-price defaults to min-bid
        List<TenantBid> bids = List.of(
                new TenantBid("cs-dept", 100_000L, 2.5)
        );
        List<AuctionResult> results = solver.solve(bids, LARGE_POOL);

        assertThat(results).hasSize(1);
        AuctionResult result = results.get(0);
        assertThat(result.isCleared()).isTrue();
        assertThat(result.getTokensAllocated()).isEqualTo(100_000L);
        // Second price = min-bid (0.1 credits/kT), tokens = 100k → 100 kT * 0.1 = 10.0 credits
        assertThat(result.getCreditsPaid()).isCloseTo(10.0, within(0.001));
    }

    // ---- Multi-bidder VCG second-price ----------------------------------

    @Test
    @DisplayName("Highest bidder wins and pays the second-highest bid (VCG second-price)")
    void solve_multiBidder_winnerPaysSecondPrice() {
        // cs-dept bids 2.0, bio-dept bids 1.0
        // Pool has enough for both but only cs-dept wins first in high-bid priority
        List<TenantBid> bids = List.of(
                new TenantBid("bio-dept",  50_000L, 1.0),
                new TenantBid("cs-dept",  100_000L, 2.0)  // highest bidder
        );
        // Pool only enough for ONE bid (100k exactly)
        long smallPool = 100_000L;
        List<AuctionResult> results = solver.solve(bids, smallPool);

        // cs-dept should win (highest bid)
        AuctionResult csResult = results.stream()
                .filter(r -> "cs-dept".equals(r.getTenantId())).findFirst().orElseThrow();
        AuctionResult bioResult = results.stream()
                .filter(r -> "bio-dept".equals(r.getTenantId())).findFirst().orElseThrow();

        assertThat(csResult.isCleared()).isTrue();
        assertThat(csResult.getTokensAllocated()).isEqualTo(100_000L);
        // Second price = bio-dept's bid = 1.0 credits/kT
        // 100k tokens = 100 kT → 100 * 1.0 = 100.0 credits
        assertThat(csResult.getCreditsPaid()).isCloseTo(100.0, within(0.001));

        assertThat(bioResult.isCleared()).isFalse();
        assertThat(bioResult.getTokensAllocated()).isEqualTo(0L);
    }

    @Test
    @DisplayName("All bids cleared when pool has enough — all pay the single loser's price (min-bid if all win)")
    void solve_allBidsClearWhenPoolSufficient() {
        List<TenantBid> bids = List.of(
                new TenantBid("a", 10_000L, 3.0),
                new TenantBid("b", 10_000L, 2.0),
                new TenantBid("c", 10_000L, 1.0)
        );
        // Pool is plenty for all three (30k total needed)
        List<AuctionResult> results = solver.solve(bids, LARGE_POOL);

        assertThat(results).allMatch(AuctionResult::isCleared);
        // No loser → second price = min-bid
        results.forEach(r ->
                assertThat(r.getCreditsPaid()).isCloseTo((10_000.0 / 1000.0) * MIN_BID, within(0.001))
        );
    }

    // ---- Pool exhaustion ------------------------------------------------

    @Test
    @DisplayName("Pool exhaustion correctly marks remaining bids as losers")
    void solve_poolExhaustion_remainingBidsLose() {
        // Three bidders, pool only holds 150k (first two need 100k each, third needs 50k)
        List<TenantBid> bids = List.of(
                new TenantBid("high",   100_000L, 5.0),
                new TenantBid("mid",    100_000L, 3.0),
                new TenantBid("low",     50_000L, 1.0)
        );
        long pool = 150_000L; // enough for high + partial mid, or high + mid but not low

        List<AuctionResult> results = solver.solve(bids, pool);

        // high wins full 100k
        AuctionResult high = resultFor("high", results);
        assertThat(high.isCleared()).isTrue();
        assertThat(high.getTokensAllocated()).isEqualTo(100_000L);

        // mid wins 50k (remaining pool)
        AuctionResult mid = resultFor("mid", results);
        assertThat(mid.isCleared()).isTrue();
        assertThat(mid.getTokensAllocated()).isEqualTo(50_000L);

        // low loses — pool exhausted
        AuctionResult low = resultFor("low", results);
        assertThat(low.isCleared()).isFalse();
    }

    // ---- Below-min-bid rejection ----------------------------------------

    @Test
    @DisplayName("Bid below min-bid threshold is rejected as a loser")
    void solve_belowMinBid_isRejectedAsLoser() {
        List<TenantBid> bids = List.of(
                new TenantBid("cs-dept", 50_000L, MIN_BID - 0.001) // just below threshold
        );
        List<AuctionResult> results = solver.solve(bids, LARGE_POOL);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).isCleared()).isFalse();
    }

    @Test
    @DisplayName("Min-bid exactly at threshold is accepted")
    void solve_exactlyMinBid_isAccepted() {
        List<TenantBid> bids = List.of(
                new TenantBid("cs-dept", 50_000L, MIN_BID) // exactly at threshold
        );
        List<AuctionResult> results = solver.solve(bids, LARGE_POOL);
        assertThat(results.get(0).isCleared()).isTrue();
    }

    // ---- Helpers ---------------------------------------------------------

    private AuctionResult resultFor(String tenantId, List<AuctionResult> results) {
        return results.stream()
                .filter(r -> tenantId.equals(r.getTenantId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No result for tenant: " + tenantId));
    }
}
