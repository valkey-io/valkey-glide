# Benchmark Baseline (Established 2025-08-26)

Source run directory: `jni_guard_20250826_142935`

All metrics are final run aggregates (finalMetrics) from each scenario.

## Small Values (100 Bytes)

| Scenario       | p50 (ms) | p99 (ms) | Mean (ms) | CPU Peak (proc %) | Peak Heap MB |
| -------------- | -------- | -------- | --------- | ----------------- | ------------ |
| 100B @ 10k QPS | 0.2804   | 1.9824   | 0.6699    | ~5.4              | 1746         |
| 100B @ 60k QPS | 0.5399   | 2.2718   | 0.8779    | ~17.5             | 2276         |
| 100B @ 90k QPS | 0.8101   | 2.4686   | 1.1044    | ~17.3             | 2452         |

## Medium Values (4KB)

| Scenario      | p50 (ms)                      | p99 (ms) | Mean (ms) | CPU Peak (proc %) | Peak Heap MB |
| ------------- | ----------------------------- | -------- | --------- | ----------------- | ------------ |
| 4KB @ 10k QPS | 0.3215                        | 2.0318   | 0.6785    | ~6.0              | 2040         |
| 4KB @ 40k QPS | (see run logs; add if needed) |          |           |                   |              |
| 4KB @ 70k QPS | (see run logs; add if needed) |          |           |                   |              |

## Large Values (25KB)

| Scenario       | p50 (ms) | p99 (ms) | Mean (ms) | CPU Peak (proc %) | Peak Heap MB |
| -------------- | -------- | -------- | --------- | ----------------- | ------------ |
| 25KB @ 10k QPS | 0.4306   | 2.0392   | 0.7576    | ~9.8              | 2304         |

Notes:

1. CPU peak values approximated from periodic log summaries (process CPU). For precise baselining, capture a structured export.
2. Heap MB figures taken from log peak lines; GC tuning unchanged from prior runs.
3. Future comparisons should treat +/-5% changes in p50 or mean, and +/-3% in p99, as potentially significant pending repeated-run confirmation.
4. The 4KB @ 40k / 70k QPS scenarios and intermediate large-value QPS tiers can be appended after extracting their JSON summaries (omitted here for brevity).

Procedure to update baseline:

1. Run `./benchmarks/run-guard-benchmarks.sh`.
2. Confirm stability across at least two consecutive runs before replacing this file.
3. Record environment diffs (JVM flags, GLIBC, kernel) alongside any baseline update.

Last updated: 2025-08-26
