package com.say5.adstream.auction;

import java.util.Objects;

/**
 * One bidder's response to a BidRequest. priceMicros is what the
 * bidder is willing to pay if they win; the auction engine picks
 * the winner but charges the second-highest price (Vickrey).
 */
public final class Bid {

    private final String bidderId;
    private final String requestId;
    private final long priceMicros;
    /** Optional creative quality score; tie-breaker if prices match. */
    private final double qualityScore;

    public Bid(String bidderId, String requestId, long priceMicros, double qualityScore) {
        this.bidderId = Objects.requireNonNull(bidderId);
        this.requestId = Objects.requireNonNull(requestId);
        this.priceMicros = priceMicros;
        this.qualityScore = qualityScore;
    }

    public String bidderId() { return bidderId; }
    public String requestId() { return requestId; }
    public long priceMicros() { return priceMicros; }
    public double qualityScore() { return qualityScore; }

    @Override
    public String toString() {
        return "Bid{" + bidderId + " @" + priceMicros + "µ q=" + qualityScore + "}";
    }
}
