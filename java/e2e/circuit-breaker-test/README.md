# Client-Wide Circuit Breaker E2E Test

Proves the client-wide circuit breaker improves behavior under a slow-node scenario.
Not part of CI, run manually.

## Quick Benchmark (default)

Runs two 30s phases (without CB, then with CB) and prints a comparison table.

```bash
./run-test.sh
```

## Long-Running Stability Test

Runs with CB enabled, periodically injecting and restoring failures.
Proves CB handles repeated failure cycles without degradation.

```bash
./run-test.sh long                                               # 1 hour, inject every 5 min
DURATION_SECS=86400 INJECT_INTERVAL_SECS=600 ./run-test.sh long  # 24 hours (24h without CB + 24h with CB)
```

## What It Tests

- 3-node Valkey cluster in Docker
- 500-thread ForkJoinPool with `managedBlock()` (matches Akka/Play pattern)
- One node made sick via `DEBUG SLEEP 150ms` every 1s
- `requestTimeout=100ms`, so commands during sleep timeout
- Measures: throughput, fast errors (<10ms, CB rejections), slow errors (>=10ms, timeouts), pool size

## Expected Results

| Metric | Without CB | With CB |
|--------|-----------|---------|
| Fast errors | Majority of errors | Higher proportion (CB adds instant rejections) |
| Slow errors | Significant portion (timeouts) | Reduced (fewer commands wait for timeout) |
| Throughput | Baseline | Similar |
| Pool size | Baseline | Similar |
