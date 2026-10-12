package com.nexus.market.pricing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link StackelbergPricingEngine}.
 *
 * <p>No Spring context loaded. All configuration values are injected
 * via {@link ReflectionTestUtils} to mirror {@code application.properties}.
 *
 * <p>Test coverage:
 * <ul>
 *   <li>Low utilization → rate near p_min</li>
 *   <li>High utilization → rate near p_max</li>
 *   <li>Threshold boundary (inflection point)</li>
 *   <li>Sigmoid curve monotonicity</li>
 *   <li>Edge cases: U=0, U=1</li>
 *   <li>Clamping of out-of-range utilization inputs</li>
 *   <li>shouldLend / shouldBorrow follower logic</li>
 * </ul>
 */
@DisplayName("StackelbergPricingEngine Unit Tests")
class StackelbergPricingEngineTest {

    private StackelbergPricingEngine engine;

    /** Configuration mirrors application.properties defaults. */
    private static final double P_MIN      = 0.5;
    private static final double P_MAX      = 3.0;
    private static final double STEEPNESS  = 12.0;
    private static final double THRESHOLD  = 0.75;

    @BeforeEach
    void setUp() {
        engine = new StackelbergPricingEngine();
        ReflectionTestUtils.setField(engine, "pMin",                 P_MIN);
        ReflectionTestUtils.setField(engine, "pMax",                 P_MAX);
        ReflectionTestUtils.setField(engine, "steepness",            STEEPNESS);
        ReflectionTestUtils.setField(engine, "utilizationThreshold", THRESHOLD);
    }

    // ---- Exchange rate boundary values -----------------------------------

    @Test
    @DisplayName("U=0 (zero load) → rate is very close to p_min")
    void computeExchangeRate_zeroUtilization_nearPMin() {
        double rate = engine.computeExchangeRate(0.0);
        // sigmoid(12 * (0 - 0.75)) = sigmoid(-9) ≈ 0.000123 → rate ≈ p_min + tiny offset
        assertThat(rate).isCloseTo(P_MIN, within(0.01));
    }

    @Test
    @DisplayName("U=1 (full saturation) → rate is very close to p_max")
    void computeExchangeRate_fullUtilization_nearPMax() {
        double rate = engine.computeExchangeRate(1.0);
        // sigmoid(12 * (1 - 0.75)) = sigmoid(3) ≈ 0.9526 → rate ≈ p_min + (p_max - p_min)*0.95
        assertThat(rate).isGreaterThan(P_MAX * 0.90);
        assertThat(rate).isLessThanOrEqualTo(P_MAX);
    }

    @Test
    @DisplayName("U=threshold → rate is midpoint between p_min and p_max")
    void computeExchangeRate_atThreshold_isMidpoint() {
        double rate = engine.computeExchangeRate(THRESHOLD);
        // sigmoid(0) = 0.5 → rate = p_min + (p_max - p_min) * 0.5 = 1.75
        double expectedMidpoint = P_MIN + (P_MAX - P_MIN) * 0.5;
        assertThat(rate).isCloseTo(expectedMidpoint, within(0.001));
    }

    @Test
    @DisplayName("Rate is always within [p_min, p_max] regardless of input")
    void computeExchangeRate_alwaysWithinBounds() {
        double[] testValues = {-5.0, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 10.0};
        for (double u : testValues) {
            double rate = engine.computeExchangeRate(u);
            assertThat(rate)
                    .as("Rate for U=%f should be within [p_min, p_max]", u)
                    .isGreaterThanOrEqualTo(P_MIN)
                    .isLessThanOrEqualTo(P_MAX);
        }
    }

    // ---- Monotonicity ----------------------------------------------------

    @Test
    @DisplayName("Exchange rate is monotonically increasing with utilization")
    void computeExchangeRate_monotonicallyIncreasing() {
        double prev = 0.0;
        for (int i = 0; i <= 100; i++) {
            double u = i / 100.0;
            double rate = engine.computeExchangeRate(u);
            assertThat(rate).isGreaterThanOrEqualTo(prev);
            prev = rate;
        }
    }

    // ---- Parameterized spot checks --------------------------------------

    @ParameterizedTest(name = "U={0} → rate between {1} and {2}")
    @CsvSource({
            "0.0,  0.5, 0.55",
            "0.5,  0.55, 0.7",
            "0.75, 1.7, 1.8",
            "0.9,  2.5, 3.0",
            "1.0,  2.8, 3.0"
    })
    @DisplayName("Spot-check rate ranges at key utilization levels")
    void computeExchangeRate_spotChecks(double u, double lo, double hi) {
        double rate = engine.computeExchangeRate(u);
        assertThat(rate).isBetween(lo, hi);
    }

    // ---- Sigmoid utility -------------------------------------------------

    @Test
    @DisplayName("sigmoid(0) == 0.5")
    void sigmoid_atZero_isHalf() {
        assertThat(engine.sigmoid(0.0)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("sigmoid produces values in (0, 1) for all finite inputs")
    void sigmoid_alwaysInUnitInterval() {
        for (double x : new double[]{-100, -10, -1, 0, 1, 10, 100}) {
            double s = engine.sigmoid(x);
            assertThat(s).isBetween(0.0, 1.0);
        }
    }

    // ---- Follower decision logic -----------------------------------------

    @Test
    @DisplayName("shouldLend returns true when there are surplus tokens and rate > p_min")
    void shouldLend_surplusAndHighRate_returnsTrue() {
        double highRate = P_MIN + 0.1; // just above minimum
        assertThat(engine.shouldLend(highRate, 500_000L)).isTrue();
    }

    @Test
    @DisplayName("shouldLend returns false when no surplus")
    void shouldLend_noSurplus_returnsFalse() {
        assertThat(engine.shouldLend(P_MAX, 0L)).isFalse();
    }

    @Test
    @DisplayName("shouldLend returns false at exactly p_min")
    void shouldLend_atPMin_returnsFalse() {
        assertThat(engine.shouldLend(P_MIN, 100_000L)).isFalse();
    }

    @Test
    @DisplayName("shouldBorrow returns true when there is a deficit and rate is below near-ceiling")
    void shouldBorrow_deficitAndAcceptableRate_returnsTrue() {
        assertThat(engine.shouldBorrow(10_000L, P_MAX * 0.9)).isTrue();
    }

    @Test
    @DisplayName("shouldBorrow returns false when no deficit")
    void shouldBorrow_noDeficit_returnsFalse() {
        assertThat(engine.shouldBorrow(0L, P_MIN)).isFalse();
    }

    @Test
    @DisplayName("shouldBorrow returns false near p_max panic pricing")
    void shouldBorrow_nearCeiling_returnsFalse() {
        assertThat(engine.shouldBorrow(10_000L, P_MAX)).isFalse();
    }

    @Test
    @DisplayName("shouldBorrow returns false when deficit exceeds hard cap")
    void shouldBorrow_hugeDeficit_returnsFalse() {
        assertThat(engine.shouldBorrow(5_000_000L, P_MIN)).isFalse();
    }
}
