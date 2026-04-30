package com.say5.adstream.auction;

import java.util.Objects;

/**
 * One bid request from an exchange. The fields here are the minimum
 * an auction needs to evaluate competing bids: who's looking, what
 * placement, and at what reserve price.
 *
 * <p>Real OpenRTB requests carry dozens of additional fields (geo,
 * device, user segments). I keep it small here so the engine logic
 * is easy to follow; production wraps the full OpenRTB schema.
 */
public final class BidRequest {

    private final String requestId;
    private final String userId;
    private final String placementId;
    private final long timestampMs;
    /** Floor price in micro-units (1 USD = 1_000_000 micros). */
    private final long floorMicros;

    public BidRequest(
            String requestId,
            String userId,
            String placementId,
            long timestampMs,
            long floorMicros) {
        this.requestId = Objects.requireNonNull(requestId);
        this.userId = Objects.requireNonNull(userId);
        this.placementId = Objects.requireNonNull(placementId);
        this.timestampMs = timestampMs;
        this.floorMicros = floorMicros;
    }

    public String requestId() { return requestId; }
    public String userId() { return userId; }
    public String placementId() { return placementId; }
    public long timestampMs() { return timestampMs; }
    public long floorMicros() { return floorMicros; }

    @Override
    public String toString() {
        return "BidRequest{" + requestId + ", user=" + userId + ", placement="
                + placementId + ", floor=" + floorMicros + "µ}";
    }
}
