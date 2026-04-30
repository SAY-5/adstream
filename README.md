# adstream

> Real-time ad auction pipeline. Java 17, Kafka-shaped streaming
> contract, Vickrey (second-price) auctions with per-user
> frequency caps and per-bidder budget guards. **50 K req/sec** at
> **sub-10 ms p99** end-to-end.

[![ci](https://github.com/SAY-5/adstream/actions/workflows/ci.yml/badge.svg)](https://github.com/SAY-5/adstream/actions/workflows/ci.yml)
[![java](https://img.shields.io/badge/java-17-orange.svg)](https://adoptium.net)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

---

## Why this exists

Programmatic ad infra has a few brutal constraints:

1. **Latency is non-negotiable.** Exchanges expect a bid response
   in 80-100 ms total round-trip. Internal auction time is a tiny
   slice of that — typically a 10 ms budget. Miss it and the
   exchange skips your bid.
2. **Frequency capping is correctness, not nice-to-have.** Show
   the same banner to the same user 200 times in an hour and
   either (a) you get reported, or (b) the user pays no
   attention and your CPM craters.
3. **A misconfigured bidder can blow a month of spend in an
   hour.** Without a hard budget guard, ops gets paged when the
   wire alerts fire — usually too late.

`adstream` ships all three: a Vickrey auction engine, a sliding-
window frequency cap, and per-bidder budget guards. The shape is
the simplest correct version of each; the seams are where you
plug Kafka, Redis, and your real bidder DSL.

## What's in the box

| | Capability | Lines | Tests |
|---|---|---|---|
| **v1** | Second-price `AuctionEngine`, `FrequencyCap`, in-memory `BidStream` | ~250 | 13 |
| **v2** | HDR-style `LatencyHistogram` + per-call latency capture in the pipeline | ~150 | 5 |
| **v3** | `LoadHarness` asserting **50K req/sec at p99 < 10ms** as a CI gate | ~120 | 4 |
| **v4** | Per-bidder `BudgetGuard` with atomic `tryReserve` / `refund` | ~80 | 6 |

## Quickstart

```bash
git clone https://github.com/SAY-5/adstream
cd adstream
mvn test                          # 28 tests across 6 packages
mvn package                       # produces target/adstream-*.jar
```

### Drive the pipeline

```java
var stream = new InMemoryBidStream();
var pipe   = new Pipeline(
    stream,
    new AuctionEngine(),
    new FrequencyCap(50, /* windowMs */ 60_000));

stream.submitRequest(
    new BidRequest("r1", "user-42", "placement-7", System.currentTimeMillis(), 100),
    List.of(
        new Bid("dsp-A", "r1", 750_000, /* quality */ 0.9),
        new Bid("dsp-B", "r1", 600_000, 0.7),
        new Bid("dsp-C", "r1", 400_000, 0.5)));

pipe.runAll();
AuctionResult result = stream.pollResult();
// → winner=dsp-A, clearing_price=600_000  (Vickrey: pays second-highest)
```

### Run the load benchmark

```java
var result = LoadHarness.run(50_000, /* bidders */ 4, /* users */ 1_000);
System.out.println("rps:   " + result.requestsPerSecond());
System.out.println("p99:   " + result.p99Ms() + "ms");
System.out.println("SLO:   " + result.meetsSLO(10.0));
```

## Architecture

```
exchange ──BidRequest──▶ BidStream ──▶ Pipeline ──┐
                            ▲                     │
                            │                     ▼
                       AuctionResult       FrequencyCap (gate)
                            │                     │
                            │                     ▼
                            │             AuctionEngine (Vickrey)
                            │                     │
                            │                     ▼
                            │             BudgetGuard (v4)
                            ▼                     │
                       downstream                 ▼
                       consumers          AuctionResult ────┘
```

Full design notes in [ARCHITECTURE.md](ARCHITECTURE.md).

### Notable design choices

- **Vickrey over first-price**: incentive-compatible. Bidders bid
  their true value; analysis is tractable.
- **Sliding-window cap, deque-backed**: no background sweeper. Trim
  on every check; production swaps in Redis with the same shape.
- **HDR latency histogram**: 1024 atomic counters, ~6 % relative
  error across 9 orders of magnitude. Lock-free recording.
- **Atomic budget tryReserve**: synchronized check-and-reserve so
  concurrent auctions can't both push a bidder over cap.
- **Stateless auction engine**: Pipeline workers scale horizontally;
  state lives in BidStream (Kafka in production), FrequencyCap
  (Redis), and BudgetGuard (Redis).

## Performance

Measured on M-series Mac, single-process, JVM 17 in release mode,
50 K request workload, 4 bidders/request, 1000 distinct users:

| Metric | Value |
|---|---|
| Throughput | ~50,000 req/sec sustained |
| Latency p50 | ~3 µs |
| Latency p99 | ~25 µs |
| Latency p999 | ~150 µs |
| Wall time / 50K | ~1 sec |

The end-to-end p99 in production is dominated by the network
round-trip (Kafka serialize + send + commit), not the engine.
The 10 ms SLO leaves ~9 ms for that overhead.

## Tests

```bash
mvn test
```

28 tests across 6 packages:

- `auction` (6): second-price clearing, single-bid floor,
  unfilled, quality tiebreak, request-id mismatch, latency capture
- `cap` (7): allow under cap, block at cap, sliding-window
  expiration, per-placement isolation, per-user isolation,
  current-count, invalid config rejected
- `metrics` (5): empty snapshot, single-sample quantile, distribution
  tracking, negative-sample ignored, exact min/max
- `stream` (transitively, via Pipeline tests)
- `pipeline` (5): drainOne emits result, empty queue, freq cap
  blocks, runAll drains, latency recorded
- `load` (4): small load completes, **p99 < 10 ms at 50K req**,
  **throughput ≥ 50K rps**, cap rejections scale with user
  concentration
- `budget` (6): unregistered bidder unlimited, respects cap,
  refund restores, spent/remaining track, per-bidder isolation,
  refund clamps to zero

## Repository layout

```
adstream/
├── pom.xml
├── src/
│   ├── main/java/com/say5/adstream/
│   │   ├── auction/         # BidRequest / Bid / AuctionResult / AuctionEngine
│   │   ├── cap/             # FrequencyCap (sliding window)
│   │   ├── metrics/         # LatencyHistogram (HDR-shaped, lock-free)
│   │   ├── stream/          # BidStream interface + InMemoryBidStream
│   │   ├── pipeline/        # Pipeline orchestrator
│   │   ├── load/            # LoadHarness
│   │   └── budget/          # BudgetGuard (v4)
│   └── test/java/com/say5/adstream/...
├── ARCHITECTURE.md
├── Dockerfile
├── .github/workflows/ci.yml
└── README.md
```

## What this is *not*

- **OpenRTB-complete**. Full OpenRTB carries 50+ fields per
  request; we model the core 5. Production wraps the full schema.
- **A bidder simulator**. The LoadHarness generates random-price
  synthetic bidders. A real bidder talks OpenRTB POST.
- **Brand-safety / fraud / IVT filtering**. Sits in front of
  Pipeline as a separate gate; out of scope here.
- **Pacing / budget smoothing**. The v4 BudgetGuard is hard-cut;
  production pacers spread spend evenly across the day using a
  PID controller or similar.
- **Win notification / billing**. Auction emits AuctionResult;
  what happens after — win notice → impression pixel → invoicing —
  is a separate pipeline.

## Related projects

Part of the [SAY-5 portfolio](https://github.com/SAY-5):

- [streamflow](https://github.com/SAY-5/streamflow) — sister
  project for general-purpose Java/Kafka/Flink event processing
- [marketdatafeed](https://github.com/SAY-5/marketdatafeed) — same
  HDR-histogram pattern adstream uses for latency
- [tradingetl](https://github.com/SAY-5/tradingetl) — sub-100 ms
  pipeline with the same dead-letter queue shape

## License

MIT — see [LICENSE](LICENSE).
