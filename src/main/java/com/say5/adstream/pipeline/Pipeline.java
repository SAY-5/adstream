package com.say5.adstream.pipeline;

import com.say5.adstream.auction.AuctionEngine;
import com.say5.adstream.auction.AuctionResult;
import com.say5.adstream.cap.FrequencyCap;
import com.say5.adstream.metrics.LatencyHistogram;
import com.say5.adstream.stream.InMemoryBidStream;

/**
 * Wires the BidStream → FrequencyCap gate → AuctionEngine → results
 * topic. {@code drainOne()} pulls one pending request, runs it
 * through the pipeline, records the latency. {@code runAll()}
 * loops until the request queue is empty — what the load harness
 * calls.
 *
 * <p>The end-to-end latency includes: dequeue + cap check + sort +
 * pick winner + clearing-price calc + emit result. We measure
 * wall-clock around all of it and feed into {@link LatencyHistogram}
 * so the SLO (p99 < 10 ms) can be asserted.
 */
public final class Pipeline {

    private final InMemoryBidStream stream;
    private final AuctionEngine engine;
    private final FrequencyCap cap;
    private final LatencyHistogram latency = new LatencyHistogram();
    private long processed = 0L;
    private long capRejected = 0L;

    public Pipeline(InMemoryBidStream stream, AuctionEngine engine, FrequencyCap cap) {
        this.stream = stream;
        this.engine = engine;
        this.cap = cap;
    }

    /** @return true if work was done; false if the queue was empty. */
    public boolean drainOne() {
        var pending = stream.pollRequest();
        if (pending == null) return false;

        long start = System.nanoTime();
        if (!cap.allow(pending.request().userId(), pending.request().placementId(),
                       pending.request().timestampMs())) {
            capRejected++;
            // Emit an unfilled result so downstream sees the
            // rejection rather than a missing record.
            stream.submitResult(new AuctionResult(
                    pending.request().requestId(), null, 0L,
                    System.nanoTime() - start));
            latency.record(System.nanoTime() - start);
            return true;
        }
        AuctionResult result = engine.run(pending.request(), pending.bids());
        if (result.isFilled()) {
            cap.record(pending.request().userId(), pending.request().placementId(),
                       pending.request().timestampMs());
        }
        stream.submitResult(result);
        latency.record(System.nanoTime() - start);
        processed++;
        return true;
    }

    public int runAll() {
        int n = 0;
        while (drainOne()) n++;
        return n;
    }

    public LatencyHistogram.Snapshot latencySnapshot() { return latency.snapshot(); }
    public long processed() { return processed; }
    public long capRejected() { return capRejected; }
}
