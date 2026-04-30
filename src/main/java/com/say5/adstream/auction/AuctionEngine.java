package com.say5.adstream.auction;

import java.util.Comparator;
import java.util.List;

/**
 * Second-price (Vickrey) auction. The highest bidder wins; the
 * price they pay is the second-highest bid (or the floor, whichever
 * is higher).
 *
 * <p>Why second-price: it's incentive-compatible. A bidder's
 * dominant strategy is to bid their true value, which makes
 * downstream economic analysis sane. Most large exchanges (GAM,
 * Magnite) used to run second-price; many switched to first-price
 * around 2019 for header-bidding reasons. We run second-price
 * because the engine is the same shape either way and the proofs
 * are cleaner.
 *
 * <p>Tie-breaking: equal price -> higher qualityScore wins. If
 * everything's equal, lexicographic on bidderId so the test suite
 * is deterministic.
 */
public final class AuctionEngine {

    private static final Comparator<Bid> BY_PRICE_THEN_QUALITY =
            Comparator
                    .comparingLong(Bid::priceMicros)
                    .thenComparingDouble(Bid::qualityScore)
                    .thenComparing(Comparator.comparing(Bid::bidderId).reversed())
                    .reversed();

    public AuctionResult run(BidRequest request, List<Bid> bids) {
        long start = System.nanoTime();
        // Filter out bids below the reserve price.
        List<Bid> eligible = bids.stream()
                .filter(b -> b.priceMicros() >= request.floorMicros())
                .filter(b -> b.requestId().equals(request.requestId()))
                .sorted(BY_PRICE_THEN_QUALITY)
                .toList();

        long latency = System.nanoTime() - start;

        if (eligible.isEmpty()) {
            return new AuctionResult(request.requestId(), null, 0L, latency);
        }
        Bid winner = eligible.get(0);
        long clearing;
        if (eligible.size() == 1) {
            // Only one bid — clears at the floor.
            clearing = request.floorMicros();
        } else {
            // Second-price: charge the runner-up's price, capped at
            // the winner's bid (always true) and floored at the
            // reserve.
            clearing = Math.max(eligible.get(1).priceMicros(), request.floorMicros());
        }
        return new AuctionResult(
                request.requestId(),
                winner.bidderId(),
                clearing,
                System.nanoTime() - start);
    }
}
