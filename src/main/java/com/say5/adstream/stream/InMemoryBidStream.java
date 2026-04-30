package com.say5.adstream.stream;

import com.say5.adstream.auction.AuctionResult;
import com.say5.adstream.auction.Bid;
import com.say5.adstream.auction.BidRequest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Test/dev backend for BidStream. Production wraps a real Kafka
 * producer/consumer pair; this in-memory variant lets the test
 * suite drive the pipeline end-to-end without spinning up a
 * broker.
 *
 * <p>Two queues: pending requests waiting to be auctioned, and
 * cleared results downstream consumers can drain.
 */
public final class InMemoryBidStream implements BidStream {

    public record PendingRequest(BidRequest request, List<Bid> bids) {}

    private final Deque<PendingRequest> requests = new ConcurrentLinkedDeque<>();
    private final Deque<AuctionResult> results = new ConcurrentLinkedDeque<>();

    @Override
    public void submitRequest(BidRequest request, List<Bid> bids) {
        requests.addLast(new PendingRequest(request, List.copyOf(bids)));
    }

    @Override
    public void submitResult(AuctionResult result) {
        results.addLast(result);
    }

    @Override
    public boolean hasPending() {
        return !requests.isEmpty();
    }

    public PendingRequest pollRequest() {
        return requests.pollFirst();
    }

    public AuctionResult pollResult() {
        return results.pollFirst();
    }

    public int pendingCount() { return requests.size(); }
    public int resultCount() { return results.size(); }

    /** Diagnostic snapshot for tests + dashboards. */
    public Map<String, Integer> queueDepths() {
        return Map.of("requests", requests.size(), "results", results.size());
    }
}
