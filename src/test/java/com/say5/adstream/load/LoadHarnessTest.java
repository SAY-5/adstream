package com.say5.adstream.load;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoadHarnessTest {

    @Test
    void smallLoadCompletes() {
        var result = LoadHarness.run(1_000, 4, 100);
        assertTrue(result.processed() + result.capRejected() == 1_000);
    }

    @Test
    void p99UnderTenMillisecondsAtFiftyThousand() {
        var result = LoadHarness.run(50_000, 4, 1_000);
        // SLO: p99 < 10 ms. CI gate uses this assertion.
        assertTrue(result.meetsSLO(10.0),
                "p99=" + result.p99Ms() + "ms, expected <=10ms");
    }

    @Test
    void throughputAboveFiftyThousandPerSecond() {
        var result = LoadHarness.run(50_000, 4, 1_000);
        // Headline number from the resume bullet.
        assertTrue(result.requestsPerSecond() >= 50_000,
                "rps=" + result.requestsPerSecond());
    }

    @Test
    void capRejectionsScaleWithUserConcentration() {
        // 100 requests, 5 users → ~20 per user. Cap is 50 → none
        // rejected.
        var loose = LoadHarness.run(100, 4, 5);
        assertTrue(loose.capRejected() == 0);
        // 1000 requests, 5 users → 200 per user. Cap is 50 → 750
        // rejected.
        var tight = LoadHarness.run(1_000, 4, 5);
        assertTrue(tight.capRejected() > 500);
    }
}
