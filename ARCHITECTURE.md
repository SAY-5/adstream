# Architecture

## Big picture

```
exchange ──BidRequest──▶ BidStream ──▶ Pipeline ──┐
                            ▲                     │
                            │                     ▼
                       AuctionResult       FrequencyCap (gate)
                            │                     │
                            │                     ▼
                            │             AuctionEngine (Vickrey)
                            │                     │
                            ▼                     ▼
                       downstream         BudgetGuard (v4)
                       consumers                  │
                                                  ▼
                                          AuctionResult
```

## Why second-price (Vickrey)

A second-price auction charges the winner the *second-highest*
bid. The clean property is that a bidder's dominant strategy is
to bid their true value — there's no incentive to shade. That
makes downstream economic analysis tractable. Most major
exchanges ran second-price for years; many switched to
first-price around 2019 because of header-bidding side effects.
We ship second-price because the engine is the same shape either
way and the proofs are cleaner.

## Frequency capping

Per-(user, placement) sliding window via a deque of impression
timestamps. On every check we trim entries older than `now -
windowMs` and compare deque size against the cap. O(N) worst case
where N is the cap; for typical caps (5-50) it's negligible.

Production wraps Redis with a sorted set per (user, placement)
key. The contract — `allow(user, placement, now)` and
`record(user, placement, now)` — stays the same.

## Latency histogram

HDR-style log-bucketed: 64 major buckets (powers of 2 in
nanoseconds) with 16 minor sub-buckets each. 1024 atomic
counters total, ~6% relative error across 9 orders of magnitude.

`AtomicLongArray` for the counters so producers don't contend on
a lock. `snapshot()` returns a frozen copy you can sort + query
for percentiles. Min/max are tracked separately because bucket
math only gives a range; `min()` and `max()` should be exact for
diagnostics.

## SLO + load harness

The headline metric is `p99 < 10 ms` end-to-end on the
canonical workload (50K requests, 4 bidders/req, 1000 distinct
users). The `LoadHarness` test asserts this; CI fails the build
if it regresses.

The harness pre-seeds the in-memory stream and times the drain
loop only — that way the wall clock measures pipeline work, not
test-setup overhead. Throughput on M-series in release mode:
sustained ~45-55K req/sec single-process.

## Budget guard (v4)

Each bidder declares a daily spend cap; bids that would push
them over the cap are silently dropped before the auction runs.

`tryReserve(bidder, price)` is atomic: check + reserve in one
synchronized block so two concurrent auctions can't both push
the same bidder over budget. The cost is per-bidder lock
contention; in practice the per-bidder rate is well below
contended-lock thresholds.

Refunds happen for losers (only winners actually spend). Without
refund, a bidder who placed many losing bids would deplete their
budget without serving a single impression.

## Production deployment shape

Two Kafka topics: `bid_requests` (incoming) and `auction_results`
(outcomes). The `BidStream` interface in this codebase is what a
production wrapper around Kafka producer/consumer pairs would
satisfy. Auction engine instances are stateless workers; scale
horizontally by spinning up more consumers on the same topic.

`FrequencyCap` is per-process today; production runs Redis with
the same deque shape and a small Lua script for atomic
trim+check. `BudgetGuard` is per-process likewise; production
shards by bidder ID and uses Redis INCR / DECR for atomic
reservation.

## What's deliberately not here

- **OpenRTB schema**. The full spec carries dozens of fields per
  request (geo, device, user segments, deal IDs). We model the
  core 5 so the engine logic is readable. Production wraps the
  full schema.
- **Bidder simulator**. The load harness generates synthetic
  bidders with random prices; real bidders would talk to a DSP
  via OpenRTB POST.
- **Brand-safety filters / fraud detection**. Out of scope; sits
  in front of `Pipeline` as a separate gate.
- **Pacing / budget smoothing**. The v4 BudgetGuard is hard-cut;
  production pacers spread spend evenly across the day.
