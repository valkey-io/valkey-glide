# Inflight Pressure E2E Test

Validates the synchronous inflight limit check at the JNI boundary (#5952).

Not part of CI, run manually against a local or remote cluster.

## What It Tests

1. CLIENT PAUSE all primaries for 15s (stalls all responses)
2. Blast requests from a fixed thread pool
3. After Java timeout fires (3s), verify inflight rejections occur (proves Rust-side sync check works)
4. After pause ends, verify recovery (ops succeed again)

## Run

Requires a running Valkey cluster. Update host/port in the source, then:

```bash
./gradlew publishToMavenLocal
cd e2e/inflight-pressure-test
# Run via IDE or compile and execute manually
```
