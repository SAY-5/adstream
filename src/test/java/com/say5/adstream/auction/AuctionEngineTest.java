package com.say5.adstream.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuctionEngineTest {

    private static BidRequest req(long floor) {
        return new BidRequest("r1", "u1", "p1", 0L, floor);
    }

    private static Bid bid(String bidder, long price) {
        return new Bid(bidder, "r1", price, 1.0);
    }

    @Test
    void winnerPaysSecondPrice() {
        var result = new AuctionEngine().run(
                req(100),
                List.of(bid("a", 500), bid("b", 300), bid("c", 200)));
        assertEquals("a", result.winningBidder());
        assertEquals(300L, result.clearingPriceMicros());
    }

    @Test
    void singleBidClearsAtFloor() {
        var result = new AuctionEngine().run(
                req(150),
                List.of(bid("a", 500)));
        assertEquals("a", result.winningBidder());
        assertEquals(150L, result.clearingPriceMicros());
    }

    @Test
    void noEligibleBidsReturnsUnfilled() {
        var result = new AuctionEngine().run(
                req(1000),
                List.of(bid("a", 200), bid("b", 300)));
        assertNull(result.winningBidder());
        assertFalse(result.isFilled());
    }

    @Test
    void qualityScoreBreaksPriceTies() {
        var result = new AuctionEngine().run(
                req(100),
                List.of(
                        new Bid("a", "r1", 500, 0.5),
                        new Bid("b", "r1", 500, 0.9)));
        assertEquals("b", result.winningBidder());
    }

    @Test
    void wrongRequestIdRejected() {
        var result = new AuctionEngine().run(
                req(100),
                List.of(new Bid("a", "DIFFERENT", 500, 1.0)));
        assertNull(result.winningBidder());
    }

    @Test
    void latencyIsRecorded() {
        var result = new AuctionEngine().run(
                req(100), List.of(bid("a", 500), bid("b", 300)));
        assertTrue(result.latencyNanos() > 0);
    }
}
