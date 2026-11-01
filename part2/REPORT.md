# Project 1, Part 2

## Commit point

Repository: TODO. Branch: TODO. Commit: TODO.

## Introduction

The Functions backend moves images from Cosmos DB to Blob Storage, compares consistency policies and uses ETags to prevent lost auction updates. Results: TODO, after real measurements.

## Design

Images use the existing SHA-1 identifier as the blob name; the content type is stored in blob HTTP properties. Cosmos defaults to the account policy; the custom policy uses Strong for user/session point reads, Session for activity and Eventual for informational listings. Known id and partition-key reads use `readItem`. Auction updates use ETags; 412 triggers reread and revalidation, with at most three attempts. A rejected bid returns 403, or 409 after contention. Bid and auction are in different containers: compensation is not atomic, and crashes or uncertain writes require reconciliation.

## Evaluation

Region/tier: TODO. Client/machine: TODO. Dataset/images: TODO. Workload/duration/load: TODO. Repetitions and cold/warm starts: TODO. Compare equivalent datasets and account for network, cache and throttling.

| Media | Upload p50/p95/p99 | Download p50/p95/p99 | Throughput | RU | 429 |
| --- | --- | --- | --- | --- | --- |
| Cosmos baseline | TODO | TODO | TODO | TODO | TODO |
| Blob | TODO | TODO | TODO | TODO | TODO |

| Consistency | p50/p95/p99 | Throughput | RU | Normalized RU | 429/errors |
| --- | --- | --- | --- | --- | --- |
| Session | TODO | TODO | TODO | TODO | TODO |
| Strong | TODO | TODO | TODO | TODO | TODO |
| Custom | TODO | TODO | TODO | TODO | TODO |

| Read | RU | Latency | Documents |
| --- | --- | --- | --- |
| Query | TODO | TODO | TODO |
| Point read | TODO | TODO | TODO |

| Bids | p50/p95/p99 | Accepted/rejected | Conflicts/retries | Incorrect runs |
| --- | --- | --- | --- | --- |
| Before | TODO | TODO | TODO | TODO |
| ETag | TODO | TODO | TODO | TODO |

Discuss measured effects and possible confounders: TODO. Baseline versus final changes multiple factors; do not attribute the entire difference to one change.

## Cost

Region, price date, Cosmos offer/RU, Storage redundancy and access tier: TODO. Cosmos image cost includes storage and request RUs; Blob includes storage, writes, reads, retrieval and transfer where applicable. Use regional prices and measured operation counts: TODO. Compare the observed RU and latency of each consistency level: TODO.

## Conclusion

The implementation reduces Cosmos image storage and guards auction updates against stale ETags. Complete the outcome after testing: TODO. Limitations include the lack of atomic writes across bid and auction containers, legacy image migration, and cross-instance Session tokens; further work would add durable reconciliation and repeat measurements across loads.
