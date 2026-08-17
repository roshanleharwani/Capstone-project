package com.nexus.router.guard;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CascadeGuardTest {

    private final CascadeGuard guard = new CascadeGuard();

    @Test
    void testShouldFastPath() {
        assertTrue(guard.shouldFastPath(Map.of("prompt", "Hello")));
        assertFalse(guard.shouldFastPath(Map.of("prompt", "This is a much longer prompt that definitely should not be fast-pathed.")));
    }

    @Test
    void testEvaluateReliability() {
        assertTrue(guard.evaluateReliability(Map.of("confidence", 0.95)));
        assertFalse(guard.evaluateReliability(Map.of("confidence", 0.70)));
        // Default when confidence is missing is 0.9 (reliable)
        assertTrue(guard.evaluateReliability(Map.of()));
    }
}
