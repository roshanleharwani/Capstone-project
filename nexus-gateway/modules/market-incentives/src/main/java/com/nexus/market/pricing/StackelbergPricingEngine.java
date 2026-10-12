package com.nexus.market.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stackelberg Dynamic Pricing Engine — Core Task 2.
 *
 * <p>Implements the two-stage Stackelberg game for token exchange rate setting:
 *
 * <ul>
 *   <li><b>Stage 1 — Gateway Leader:</b> As system-wide API token utilization {@code U}
 *       approaches daily caps, NexusGateway dynamically sets an internal exchange rate
 *       {@code p_t} for borrowed tokens using a sigmoid pricing function.</li>
 *   <li><b>Stage 2 — Tenant Followers:</b> Tenants with low utilization automatically
 *       opt into the lending pool to earn future priority credits. Quota-starved
 *       tenants borrow from the pool at the current rate {@code p_t}.</li>
 * </ul>
 *
 * <p><b>Pricing formula:</b>
 * <pre>
 *   p_t = p_min + (p_max - p_min) * sigmoid(k * (U - threshold))
 *
 *   where:
 *     U         — system-wide utilization ∈ [0, 1]
 *     p_min     — minimum exchange rate (credits per 1000 tokens) at low load
 *     p_max     — maximum exchange rate at full system saturation
 *     k         — steepness parameter controlling how sharply the price rises
 *     threshold — utilization level where pricing begins to accelerate (inflection point)
 * </pre>
 *
 * <p>The sigmoid function ensures:
 * <ul>
 *   <li>Price stays near {@code p_min} for normal loads (U &lt; threshold)</li>
 *   <li>Price rises steeply as utilization crosses the threshold</li>
 *   <li>Price asymptotically approaches {@code p_max} at saturation (U → 1)</li>
 * </ul>
 *
 * <p>Reference: Section III.C of the NexusGateway design paper.
 */
@Service
public class StackelbergPricingEngine {

    private static final Logger log = LoggerFactory.getLogger(StackelbergPricingEngine.class);

    /** Minimum exchange rate (credits per 1000 tokens) at zero load. */
    @Value("${market.pricing.p-min:0.5}")
    private double pMin;

    /** Maximum exchange rate (credits per 1000 tokens) at full saturation. */
    @Value("${market.pricing.p-max:3.0}")
    private double pMax;

    /**
     * Steepness parameter {@code k}.
     * Higher values make the pricing curve steeper around the threshold.
     */
    @Value("${market.pricing.steepness:12.0}")
    private double steepness;

    /**
     * Utilization inflection point.
     * Pricing begins to accelerate rapidly beyond this value (default 0.75 = 75%).
     */
    @Value("${market.pricing.utilization-threshold:0.75}")
    private double utilizationThreshold;

    /**
     * Borrowers refuse to enter the auction when the leader price is above this
     * fraction of {@code p_max} (near-saturation / panic pricing).
     */
    @Value("${market.pricing.max-borrow-ratio:0.95}")
    private double maxBorrowRatio;

    /** Hard cap on tokens a single deficit request may attempt to borrow. */
    @Value("${market.pricing.max-borrow-tokens:2000000}")
    private long maxBorrowTokens;

    /**
     * Computes the current token exchange rate based on system-wide utilization.
     *
     * <p>This is the core Stage 1 (Leader) decision in the Stackelberg game.
     * The rate is then broadcast to all tenants (Stage 2 followers) who decide
     * whether to lend or borrow accordingly.
     *
     * @param systemUtilization the fraction of total daily token budget consumed
     *                          across all tenants, ∈ [0.0, 1.0]
     * @return the exchange rate {@code p_t} in credits per 1000 tokens
     */
    public double computeExchangeRate(double systemUtilization) {
        double u = clamp(systemUtilization, 0.0, 1.0);
        double sigmoid = sigmoid(steepness * (u - utilizationThreshold));
        double rate = pMin + (pMax - pMin) * sigmoid;
        log.debug("[Stackelberg] U={:.4f} -> sigmoid={:.4f} -> p_t={:.4f}",
                u, sigmoid, rate);
        return rate;
    }

    /**
     * Determines whether a follower-tenant should lend its surplus tokens to the
     * pool at the current exchange rate.
     *
     * <p>In the Stackelberg game, the optimal follower strategy is to lend whenever
     * the offered exchange rate exceeds their reservation value. This method uses a
     * simplified threshold: tenants opt in when the current rate exceeds {@code p_min}
     * (i.e., whenever prices are above the baseline — always profitable to lend).
     *
     * @param exchangeRate current {@code p_t} from {@link #computeExchangeRate}
     * @param remainingTokens the follower-tenant's unused tokens
     * @return {@code true} if the tenant should contribute to the lending pool
     */
    public boolean shouldLend(double exchangeRate, long remainingTokens) {
        // A tenant should lend if: (1) they have surplus and (2) the rate is above p_min
        return remainingTokens > 0 && exchangeRate > pMin;
    }

    /**
     * Returns the optimal borrowing threshold — the maximum rate a deficit tenant
     * should accept. Tenants will borrow if their token deficit cost exceeds
     * the cost of waiting, modeled as a fixed penalty factor of {@code p_max}.
     *
     * @param deficitTokens tokens the tenant still needs beyond its own quota
     * @param exchangeRate  current market rate {@code p_t}
     * @return {@code true} if the tenant should participate in the auction as a borrower
     */
    public boolean shouldBorrow(long deficitTokens, double exchangeRate) {
        if (deficitTokens <= 0 || deficitTokens > maxBorrowTokens) {
            return false;
        }
        double ceiling = pMax * Math.max(0.0, Math.min(1.0, maxBorrowRatio));
        return exchangeRate <= ceiling;
    }

    // ---- Mathematical utilities ------------------------------------------

    /**
     * Standard logistic sigmoid function: {@code 1 / (1 + e^(-x))}.
     * Maps any real number to the open interval (0, 1).
     */
    double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /** Clamps {@code value} to the interval {@code [min, max]}. */
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---- Getters (package-visible for tests) ----------------------------

    double getPMin() { return pMin; }
    double getPMax() { return pMax; }
    double getSteepness() { return steepness; }
    double getUtilizationThreshold() { return utilizationThreshold; }
}
