# Project 1, Part 4: Azure AI Search

**Group:** Martinho Pereira (66062), Wilker Martins (78013)

## Commit point


## Introduction

The original search endpoint used a Cosmos DB `CONTAINS` query, which scans auction partitions. Part 4 adds Azure AI Search and keeps the existing endpoint and query parameters. At 600 auctions, the saved load tests show slightly higher latency after the change, while direct queries show that the Cosmos search query costs 8.46 RU for each of five category searches. A controlled listing became searchable 166 seconds after creation. These results clarify query cost and freshness, but the shared account and Redis cache prevent attributing end-to-end RU savings to Search.

## Design

The Functions app is in Germany West Central and the Basic Azure AI Search service is in Norway East. A Cosmos DB indexer populates the `auctions` index every five minutes, using `_ts` as its high-water mark. `title` and `description` are searchable with the English Lucene analyzer; `id` is the index key. The index also stores `owner`, `endTime`, `minimumPrice`, `status`, and `imageId`.

Search applies `SearchMode.ALL` to `title` and `description` and requests the requested page of IDs. The Function then reads each matching auction through the existing Redis object cache, falling back to a Cosmos point read, and returns the full auction rather than the partial index document. This preserves current fields on returned items, but search membership and matching text can lag until the indexer runs.

## Evaluation

The saved Artillery runs used `search-fun.yml`: one new virtual user per second for 60 seconds, with no warm-up. Each run recorded 60 search requests. There is one pre-change run each at 100, 300, and 600 auctions, and three post-change runs at 600 auctions. Latencies are milliseconds, shown as p50 / p95 / p99. The post-change row is the median of the three run-level percentiles.

| Version and data size | p50 / p95 / p99 | Requests / HTTP 200 | Failed VUs |
| --- | ---: | ---: | ---: |
| Cosmos `CONTAINS`, 100 auctions | 327.1 / 376.2 / 383.8 | 60 / 60 | 0 |
| Cosmos `CONTAINS`, 300 auctions | 327.1 / 376.2 / 399.5 | 60 / 60 | 0 |
| Cosmos `CONTAINS`, 600 auctions | 347.3 / 391.6 / 391.6 | 60 / 60 | 0 |
| Azure AI Search, 600 auctions | 376.2 / 415.8 / 424.2 | 60 / 60 per run | 0 per run |

Compared with the 600-auction baseline, the indexed run's median p50, p95, and p99 were about 8.3%, 6.2%, and 8.3% higher. These short runs do not support the expectation of lower latency. The Search service is in a different region from Functions; its ID lookup and subsequent cache/Cosmos reads also add work, but those effects were not isolated. The baseline p95 changed little between 100 and 300 auctions and rose modestly at 600, so these samples do not establish scaling at larger sizes.

With the 600-auction category dataset, direct cross-partition `CONTAINS` queries returned 120 matches and cost 8.46 RU per query. Search returned the same 120 IDs for each category, with exact ID-set matches:

| Search term | Cosmos matches / RU | AI Search matches | IDs equal |
| --- | ---: | ---: | --- |
| astronomy | 120 / 8.46 | 120 | Yes |
| photography | 120 / 8.46 | 120 | Yes |
| cycling | 120 / 8.46 | 120 | Yes |
| cooking | 120 / 8.46 | 120 | Yes |
| music | 120 / 8.46 | 120 | Yes |

Search is token-based, not substring-equivalent: a Cosmos `CONTAINS` query for the prefix `cc26freshness` found the controlled listing, while AI Search did not match that prefix inside its longer token. Searching its exact full title did find it. One controlled listing created at 01:56:30 UTC first appeared in search after 166 seconds; the successful indexer execution ran from 01:59:11.886 to 01:59:13.858 UTC. This is one observed delay, not a guaranteed maximum. The initial indexer run processed 1,902 documents with no failures; a later incremental run processed the single test listing.

A sampled cold Cosmos point read cost 1.00 RU. Thus, if all 120 matching auctions miss Redis, hydrating the results could add roughly 120 RU; warm cache hits avoid those reads. The 8.46 RU figure is the baseline `CONTAINS` query charge, not a complete cold-versus-warm comparison of the two endpoint paths.

The local Maven suite passed 26/26 tests. Azure diagnostic settings were not configured, and the shared Cosmos account's one-minute RU metrics cannot isolate the Search indexer's consumption from other traffic. The indexer status API reports documents and failures, not its Cosmos RU charge, so indexer RU cost could not be measured independently.

## Cost

The Azure Retail Prices API listed the Basic Search unit in Norway East at USD $0.101 per hour on 2026-11-02, or about $73.73 for 730 hours of continuous operation. Germany West Central Cosmos provisioned throughput was listed at $0.008 per 100 RU/s-hour. At 8.46 RU per `CONTAINS` query, a purely linear retail-price comparison reaches Search's $0.101/hour at about 1,263 RU/s of avoided capacity, equivalent to roughly 149 searches per second. This is a theoretical capacity-equivalent threshold, not an observed break-even: the database is a shared, fixed 400 RU/s and cannot be scaled down based on this endpoint alone.

The Cosmos account is free-tier-enabled, so actual Cosmos charges may not follow the retail estimate; Search's hourly charge continues while idle. The indexed endpoint can also incur point-read RUs on cache misses, and indexer RUs, cross-region transfer, and any cache effects are excluded from the threshold. At the measured one-search-per-second load, the Search unit does not pay for itself through a reduction in provisioned Cosmos capacity.

## Conclusion

Azure AI Search replaces the cross-partition text scan and matched the five tested category searches, but its token semantics differ from substring search and the measured load tests showed no latency gain. One new listing was found after 166 seconds. The Basic unit's fixed cost is not justified by the measured one-search-per-second workload. Further work should repeat matched tests with cold and warm caches, capture per-request RU and indexer consumption, and measure freshness across multiple updates and indexer cycles.
