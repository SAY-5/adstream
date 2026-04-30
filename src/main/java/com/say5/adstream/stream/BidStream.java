package com.say5.adstream.stream;

import com.say5.adstream.auction.AuctionResult;
import com.say5.adstream.auction.Bid;
import com.say5.adstream.auction.BidRequest;

import java.util.List;

/**
 * Stream contract sitting in front of Kafka (production) or an
 * in-memory queue (tests). The auction engine + cap layer don't
 * care which one they're talking to; this interface is the seam.
 *
 * <p>{@code submitRequest} is the producer side ("a new BidRequest
 * arrived from an exchange"). {@code submitResult} is the consumer
 * side ("we cleared an auction; tell downstream"). Real Kafka usage
 * would map these to two topics — {@code bid_requests} and
 * {@code auction_results}.
 */
public interface BidStream {

    void submitRequest(BidRequest request, List<Bid> bids);

    void submitResult(AuctionResult result);

    /** True if there's still backlog to drain. Used by tests. */
    boolean hasPending();
}
