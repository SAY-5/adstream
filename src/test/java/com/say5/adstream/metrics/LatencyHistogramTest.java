package com.say5.adstream.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LatencyHistogramTest {

    @Test
    void emptySnapshotReturnsZeros() {
        var snap = new LatencyHistogram().snapshot();
        assertEquals(0L, snap.totalCount());
        assertEquals(0L, snap.quantileNs(0.5));
        assertEquals(0L, snap.meanNs());
    }

    @Test
    void singleSampleAccessibleViaQuantile() {
        var h = new LatencyHistogram();
        h.record(1000);
        var snap = h.snapshot();
        assertEquals(1L, snap.totalCount());
        // Bucket precision: p50 is somewhere in the bucket
        // containing 1000 ns. Just check it's within the bucket.
        assertTrue(snap.quantileNs(0.5) >= 1000 - 100);
    }

    @Test
    void quantilesTrackDistribution() {
        var h = new LatencyHistogram();
        for (int i = 0; i < 9000; i++) h.record(1_000);
        for (int i = 0; i < 1000; i++) h.record(100_000_000L); // tail
        var snap = h.snapshot();
        // p50 should land in the baseline bucket.
        assertTrue(snap.quantileNs(0.5) <= 2_000);
        // p99 should be deep in the tail.
        assertTrue(snap.quantileNs(0.99) >= 50_000_000L);
    }

    @Test
    void negativeSamplesIgnored() {
        var h = new LatencyHistogram();
        h.record(-1);
        h.record(100);
        assertEquals(1L, h.snapshot().totalCount());
    }

    @Test
    void minMaxTrackedExactly() {
        var h = new LatencyHistogram();
        h.record(50);
        h.record(5_000_000L);
        h.record(2_000);
        var snap = h.snapshot();
        assertEquals(50L, snap.minNs());
        assertEquals(5_000_000L, snap.maxNs());
    }
}
