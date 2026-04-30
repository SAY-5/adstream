package com.say5.adstream.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.say5.adstream.auction.AuctionEngine;
import com.say5.adstream.auction.Bid;
import com.say5.adstream.auction.BidRequest;
import com.say5.adstream.cap.FrequencyCap;
import com.say5.adstream.stream.InMemoryBidStream;

import java.util.List;
import org.junit.jupiter.api.Test;

class PipelineTest {

    private static BidRequest req(String id, String user, long ts) {
        return new BidRequest(id, user, "p1", ts, 100);
    }

    @Test
    void drainOneRunsAuctionAndEmitsResult() {
        var stream = new InMemoryBidStream();
        var pipe = new Pipeline(stream, new AuctionEngine(), new FrequencyCap(100, 60_000));
        stream.submitRequest(req("r1", "u1", 0L), List.of(
                new Bid("a", "r1", 500, 1.0),
                new Bid("b", "r1", 300, 1.0)));
        assertTrue(pipe.drainOne());
        var result = stream.pollResult();
        assertEquals("a", result.winningBidder());
        assertEquals(300, result.clearingPriceMicros());
    }

    @Test
    void emptyQueueReturnsFalse() {
        var stream = new InMemoryBidStream();
        var pipe = new Pipeline(stream, new AuctionEngine(), new FrequencyCap(10, 1000));
        assertEquals(false, pipe.drainOne());
    }

    @Test
    void frequencyCapBlocksAfterLimit() {
        var stream = new InMemoryBidStream();
        var pipe = new Pipeline(stream, new AuctionEngine(), new FrequencyCap(2, 60_000));
        for (int i = 0; i < 4; i++) {
            stream.submitRequest(req("r" + i, "u1", i * 100L), List.of(
                    new Bid("a", "r" + i, 500, 1.0)));
        }
        pipe.runAll();
        assertEquals(2, pipe.processed());
        assertEquals(2, pipe.capRejected());
        // Last two results in the stream should be unfilled.
        for (int i = 0; i < 4; i++) {
            var r = stream.pollResult();
            if (i < 2) {
                assertEquals("a", r.winningBidder());
            } else {
                assertNull(r.winningBidder());
            }
        }
    }

    @Test
    void runAllDrainsEverything() {
        var stream = new InMemoryBidStream();
        var pipe = new Pipeline(stream, new AuctionEngine(), new FrequencyCap(1000, 60_000));
        for (int i = 0; i < 50; i++) {
            stream.submitRequest(req("r" + i, "u" + i, 0L), List.of(
                    new Bid("a", "r" + i, 500, 1.0),
                    new Bid("b", "r" + i, 300, 1.0)));
        }
        int processed = pipe.runAll();
        assertEquals(50, processed);
        assertEquals(50, stream.resultCount());
    }

    @Test
    void latencyRecordedPerRequest() {
        var stream = new InMemoryBidStream();
        var pipe = new Pipeline(stream, new AuctionEngine(), new FrequencyCap(1000, 60_000));
        for (int i = 0; i < 20; i++) {
            stream.submitRequest(req("r" + i, "u" + i, 0L),
                    List.of(new Bid("a", "r" + i, 500, 1.0)));
        }
        pipe.runAll();
        var snap = pipe.latencySnapshot();
        assertEquals(20L, snap.totalCount());
        assertTrue(snap.maxNs() > 0);
    }
}
