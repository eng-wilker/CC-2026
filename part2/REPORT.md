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

## Live Part 2 test results (2026-11-01)

### Environment and method

Tests ran against the existing Azure Functions app in Germany West Central, using Linux, Node.js 22.23.4, and Artillery 2.0.34. The shared Cosmos DB account was Session consistency with a 400 RU/s shared database; the Function App used the Y1 Consumption plan and Blob used Standard LRS Hot. The Artillery test was `workload1-fun.yml`: 10-second warm-up followed by 120 seconds at one new virtual user per second. The dataset included eight generated test users, 41 bundled JPEGs (380,407 bytes total), existing shared auction data, and test-created records.

The account-level throughput and Cosmos metrics are shared with other resources and cannot be attributed solely to the Functions app. Session was run three times; Strong and custom were each run once. Workload percentiles below are the median of run-level percentiles. Part 1 results are intentionally not included; use the separate Part 1 report for comparison.

### Part 2 endpoint measurements

Latency values are milliseconds. Session results combine three runs.

| Endpoint | p50 | p95 | p99 | Requests |
| --- | ---: | ---: | ---: | ---: |
| `POST /media` (Blob upload) | 96.6 | 175.9 | 198.4 | 107 |
| `GET /media` (Blob download) | 89.1 | 122.7 | 179.5 | 2,549 |
| `POST /auction/*/bid` | 111.1 | 156.0 | 301.9 | 539 |
| All HTTP requests | 100.5 | 210.6 | 407.5 | 11,494 |

| Session repetition | Overall p50 / p95 / p99 | Requests | HTTP 404 | Failed VUs | Artillery req/s |
| --- | --- | ---: | ---: | ---: | ---: |
| 1 | 100.5 / 206.5 / 368.8 | 3,427 | 220 | 0 | 25 |
| 2 | 98.5 / 210.6 / 407.5 | 4,126 | 169 | 0 | 31 |
| 3 | 100.5 / 450.4 / 889.1 | 3,941 | 126 | 0 | 25 |

There were 515 media-download HTTP 404s across the Session runs. Existing Cosmos media documents are not migrated to Blob automatically, so the dataset includes image IDs that are not present in Blob. A separate media smoke test passed image and binary uploads/downloads, content-type preservation, duplicate-upload behavior, empty-upload rejection, and missing-media handling. The full API regression test passed 42/42 checks, including a 3 MB media round trip.

### Consistency tests and Cosmos Monitor

| Consistency mode | Runs | Overall p50 / p95 / p99 (ms) | Artillery req/s | Account RU in test window | Maximum normalized RU | Azure Monitor 429 requests |
| --- | ---: | --- | ---: | ---: | ---: | ---: |
| Session (account default) | 3 | 100.5 / 210.6 / 407.5 | 25, 31, 25 | 131,141 over 7m34s | 100% | 555 |
| Strong (account default) | 1 | 113.3 / 620.3 / 1,064.4 | 26 | 25,522 over 2m39s | 100% | 358 |
| Custom per-operation | 1 | 507.8 / 4,231.1 / 6,569.8 | 19 | 33,302 over 2m59s | 100% | 2,724 |

The Strong run had two client-visible HTTP 429s and two failed virtual users. The custom run had 43 client-visible HTTP 429s, 16 HTTP 401s, and 35 failed virtual users. The Session runs had no failed virtual users. Expected 403 bid rejections occurred; they are not throttling errors. Cosmos Monitor totals cover the complete windows, are account-wide, and include other shared-account operations. All test modes reached 100% normalized RU in at least one monitored minute. Window duration, changing shared data, request mix, cold starts, retries, and unequal repetition counts mean these are exploratory results, not a controlled statistical comparison.

### Query versus point read

The same auction document and partition key were used. Session ran 30 iterations per operation; Strong ran 10. Values are medians.

| Consistency | Operation | Iterations | RU | Latency (ms) | Documents |
| --- | --- | ---: | ---: | ---: | ---: |
| Session | Point read (`readItem`) | 30 | 1.00 | 79.4 | 1 |
| Session | Parameterized query in same partition | 30 | 2.83 | 83.5 | 1 |
| Strong | Point read (`readItem`) | 10 | 2.00 | 101.3 | 1 |
| Strong | Parameterized query in same partition | 10 | 2.83 | 168.2 | 1 |

The Strong read probe overlapped its load-test window; concurrent load may affect its latency.

### Concurrent bids and regression tests

The ETag test ran three rounds with five concurrent bidders per auction. Ten bids were accepted and five correctly rejected with HTTP 403; there were no HTTP 409 responses and zero inconsistent rounds (the persisted winner matched the highest accepted bid, and accepted bids were persisted). Bid latency across the 15 requests was p50 401.5 ms, p95 3,909.9 ms, and p99 3,909.9 ms. This test validates final state but does not count internal ETag retries. The local Java unit suite passed 13/13 tests.

### Cost and resource observations

Azure Retail Prices API rates checked 2026-11-01 for Germany West Central (USD): Cosmos provisioned throughput $0.008 per 100 RU/s-hour, Cosmos data storage $0.25/GB-month; free-tier rates for those meters are $0 while within the applicable allowance. Blob Hot LRS storage was $0.0196/GB-month, write operations $0.054 per 10,000, and read operations $0.0043 per 10,000. Actual bill amounts were not available, and free-tier allowances apply to total account usage.

The Cosmos `media` container reported 491,520 bytes using Azure's deprecated DataUsage metric. During the combined test windows, the Blob Storage account reported 8,628 transactions, 6,028,624 bytes ingress, and 465,930,463 bytes egress. These storage metrics include Functions runtime traffic and are not attributable only to media requests. Storage Blob Data Reader permission was unavailable, so Blob container bytes and actual bill totals could not be verified.

### Limitations and retained evidence

The tests used a shared database with 400 RU/s throughput and existing data. Media 404s demonstrate that legacy Cosmos image records need migration or a recreated dataset before a fair media comparison. Strong and custom need at least two more controlled repetitions; the shared-account saturation and different request counts prevent attributing observed performance differences to consistency alone. A fair Part 1/Part 2 comparison also requires equivalent data and workload windows.

Raw Artillery JSON, UTC run windows, and read-comparison CSVs are retained locally under `cc-proj-test/results/part2/`; the results directory is ignored by Git. Test users, auctions, bids, and blobs created during these tests remain in the shared environment. The local ignored `users.data` contains generated passwords and must not be shared or committed. After testing, the Cosmos default was restored to Session and the Function App mode to `account`; the app health endpoint returned `cc2627-proj-fun v1`.
