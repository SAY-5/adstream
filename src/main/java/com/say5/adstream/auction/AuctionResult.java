package com.say5.adstream.auction;

/**
 * Outcome of an auction. winningBidder is null when the auction
 * had no eligible bids (frequency cap hit, no bidders responded
 * above the floor, etc.). clearingPriceMicros is the price the
 * winner actually pays — second-price under Vickrey, equal to
 * priceMicros under first-price.
 */
public record AuctionResult(
        String requestId,
        String winningBidder,
        long clearingPriceMicros,
        long latencyNanos) {

    public boolean isFilled() {
        return winningBidder != null;
    }
}
