# Project 1, Part 3: Redis Cache

**Group:** Martinho Pereira (66062), Wilker Martins (78013)

## Commit point



## Introduction

Part 3 adds Azure Managed Redis to reduce repeated Cosmos DB reads. Under the same one-arrival-per-second Artillery profile, the cached run lowered overall p95 latency from 1,153 ms to 279 ms and removed client-visible HTTP 429s. All 130 virtual users completed, compared with 120 of 130 before caching. The implementation remains backed by Cosmos DB, so cache expiry or Redis unavailability does not make auction or session data unavailable.

## Design

The Function App runs in Germany West Central; Redis is a Balanced B0 instance in Austria East (`NoCluster`), the available permitted region/SKU. Object and session entries use a 300-second TTL. Popular, recent, and about-to-close listing snapshots are refreshed by a one-minute timer and use a 90-second TTL. Writes update or invalidate related entries. Cache errors fall back to Cosmos DB. Auction updates fetch fresh Cosmos ETags rather than trusting cached values, preserving optimistic concurrency.

## Evaluation

The baseline was captured before deployment; both standard runs used `workload1-fun.yml`, a 10-second warm-up, 120 seconds at one new virtual user per second, and the same shared 400 RU/s Cosmos database. Tests ran against dynamic data, so the two datasets were not identical. Latencies are milliseconds; each cell is p50 / p95 / p99.

| Endpoint | Before cache | With cache |
| --- | ---: | ---: |
| Popular auctions | 685.5 / 2,143.5 / 4,065.2 | 108.9 / 183.1 / 247.2 |
| Recent auctions | 450.4 / 1,525.7 / 2,566.3 | 108.9 / 162.4 / 247.2 |
| About-to-close auctions | 424.2 / 1,465.9 / 1,901.1 | 106.7 / 127.8 / 133.0 |
| All HTTP requests | 125.2 / 1,153.1 / 2,276.1 | 100.5 / 278.7 / 407.5 |

| Standard run | Requests | VUs completed / created | Client HTTP 429 | Failed VUs |
| --- | ---: | ---: | ---: | ---: |
| Before cache, 1 arrival/s | 4,340 | 120 / 130 | 13 | 10 |
| With cache, 1 arrival/s | 4,315 | 130 / 130 | 0 | 0 |

The cached implementation also completed the higher offered loads of 2, 4, and 6 arrivals/s: 260/260, 520/520, and 780/780 virtual users respectively, with no client HTTP 429s. At 8 arrivals/s, 1,037 of 1,040 completed; the run returned 511 HTTP 429s, 284 HTTP 401s, 10 HTTP 500s, and three socket timeouts. Thus 6 arrivals/s was the highest tested sustainable rate; saturation occurred between 6 and 8 arrivals/s. The high-load HTTP 500s appeared during bid creation; their cause was not isolated. The live API regression suite passed 42/42 checks, and the local Java suite passed 22/22 tests.

Azure Monitor recorded 37,265 Cosmos RUs and a 100% maximum normalized RU in the baseline window, versus approximately 6,928 RUs and 43% in the cached one-arrival window. Account-level 429s were 1,402 and zero respectively. These Cosmos metrics are shared-account measurements, not isolated app attribution. Redis reported cache hits and no evictions during the observed windows; the timer refresh succeeded repeatedly. Listing results can be up to about one minute old during normal refresh, with a 90-second TTL as an expiry/fallback bound. Cache staleness was not independently measured.

## Cost

The Azure Retail Prices API listed Balanced B0 in Austria East at USD $0.019 per hour on 2026-11-02, approximately $13.87 for 730 hours of continuous operation. This is a retail estimate, not the subscription’s actual bill. Cross-region data transfer, Function execution, Cosmos throughput/storage, and other account costs are excluded. Because Redis is in a different region from the Function App, its network latency and transfer charges should be considered; the instance continues to incur hourly cost while idle.

## Conclusion

Redis substantially improved the measured listing and overall tail latency and allowed the tested workload to scale from one to six arrivals per second without failed virtual users or client 429s. The 8-arrival run exceeded the sustainable range. Results are exploratory: the shared Cosmos account, changing data, non-identical runs, and cross-region deployment limit causal and cost attribution. Cosmos remains the source of truth, and fresh ETags protect auction writes from stale-cache conflicts.
