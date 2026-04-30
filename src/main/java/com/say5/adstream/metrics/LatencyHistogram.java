package com.say5.adstream.metrics;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * HDR-style log-bucketed latency histogram. 64 major buckets (powers
 * of 2 in nanoseconds, covering ~1 ns to ~10^19 ns) with 16 minor
 * sub-buckets each — 1024 counters total, ~6% relative error across
 * 9 orders of magnitude.
 *
 * <p>AtomicLongArray for the counters so multiple producer threads
 * can record without locking. {@code snapshot()} returns a frozen
 * copy you can sort + query for percentiles.
 *
 * <p>Why this matters for an ad auction: the SLO is p99 < 10 ms
 * end-to-end. A linear-bucketed histogram either eats memory or
 * loses precision at the tail. HDR has bounded memory + bounded
 * relative error.
 */
public final class LatencyHistogram {

    public static final int MAJOR = 64;
    public static final int MINOR = 16;
    private static final int BUCKETS = MAJOR * MINOR;

    private final AtomicLongArray buckets = new AtomicLongArray(BUCKETS);
    private final java.util.concurrent.atomic.AtomicLong totalCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong totalNs = new java.util.concurrent.atomic.AtomicLong();
    private volatile long minNs = Long.MAX_VALUE;
    private volatile long maxNs = 0L;

    public void record(long ns) {
        if (ns < 0) return;
        int idx = bucketIndex(ns);
        buckets.incrementAndGet(idx);
        totalCount.incrementAndGet();
        totalNs.addAndGet(ns);
        // Best-effort min/max; small race window is acceptable for
        // diagnostics.
        if (ns < minNs) minNs = ns;
        if (ns > maxNs) maxNs = ns;
    }

    public Snapshot snapshot() {
        long[] copy = new long[BUCKETS];
        for (int i = 0; i < BUCKETS; i++) copy[i] = buckets.get(i);
        return new Snapshot(copy, totalCount.get(), totalNs.get(), minNs, maxNs);
    }

    private static int bucketIndex(long ns) {
        if (ns == 0) return 0;
        int msb = 63 - Long.numberOfLeadingZeros(ns);
        if (msb >= MAJOR) msb = MAJOR - 1;
        int minor = (msb < 4)
                ? (int) (ns & (MINOR - 1))
                : (int) ((ns >>> (msb - 4)) & (MINOR - 1));
        int idx = msb * MINOR + minor;
        return Math.min(idx, BUCKETS - 1);
    }

    private static long bucketLowerNs(int idx) {
        int major = idx / MINOR;
        int minor = idx % MINOR;
        if (major < 4) return minor;
        return (1L << major) | ((long) minor << (major - 4));
    }

    public static final class Snapshot {
        private final long[] buckets;
        private final long totalCount;
        private final long totalNs;
        private final long minNs;
        private final long maxNs;

        Snapshot(long[] buckets, long totalCount, long totalNs, long minNs, long maxNs) {
            this.buckets = buckets;
            this.totalCount = totalCount;
            this.totalNs = totalNs;
            this.minNs = (totalCount == 0) ? 0 : minNs;
            this.maxNs = maxNs;
        }

        public long totalCount() { return totalCount; }
        public long minNs() { return minNs; }
        public long maxNs() { return maxNs; }
        public long meanNs() { return totalCount == 0 ? 0 : totalNs / totalCount; }

        public long quantileNs(double q) {
            if (totalCount == 0) return 0;
            long target = (long) Math.ceil(totalCount * q);
            if (target == 0) target = 1;
            long cum = 0;
            for (int i = 0; i < buckets.length; i++) {
                cum += buckets[i];
                if (cum >= target) {
                    long lower = bucketLowerNs(i);
                    if (lower < minNs) lower = minNs;
                    if (lower > maxNs) lower = maxNs;
                    return lower;
                }
            }
            return maxNs;
        }

        @Override
        public String toString() {
            return "Snapshot{n=" + totalCount + ", min=" + minNs + "ns, max="
                    + maxNs + "ns, p50=" + quantileNs(0.5) + "ns, p99="
                    + quantileNs(0.99) + "ns, p999=" + quantileNs(0.999) + "ns}";
        }

        long[] bucketsCopy() { return Arrays.copyOf(buckets, buckets.length); }
    }
}
