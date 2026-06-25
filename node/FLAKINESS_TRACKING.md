# Node Test Flakiness Tracking — Windows/WSL

Goal: identify and fix all flaky tests so the full suite runs cleanly on Windows.

## Methodology
- Each test file is run 100 times in isolation
- Failures are categorized by type
- Fix options are proposed per file

## Test File Status

| Test File | Runs | Pass Rate | Failure Type | Fix Options | Status |
|---|---|---|---|---|---|
| AddressResolver.test.ts | - | - | - | - | 🔲 pending |
| AuthTest.test.ts | - | - | - | - | 🔲 pending |
| ClientSideCache.test.ts | - | - | - | - | 🔲 pending |
| Compression.test.ts | - | - | - | - | 🔲 pending |
| CompressionMaxSize.test.ts | - | - | - | - | 🔲 pending |
| Dns.test.ts | - | - | - | - | 🔲 pending |
| GlideClient.test.ts | - | - | - | - | 🔲 pending |
| GlideClusterClient.test.ts | - | - | - | - | 🔲 pending |
| GlideClientInternals.test.ts | - | - | - | - | 🔲 pending |
| NapiClient.test.ts | - | - | - | - | 🔲 pending |
| NodeDiscoveryMode.test.ts | - | - | - | - | 🔲 pending |
| OpenTelemetry.test.ts | - | - | - | - | 🔲 pending |
| PubSub.test.ts | - | - | - | - | 🔲 pending |
| ReadOnlyMode.test.ts | - | - | - | - | 🔲 pending |
| ScanTest.test.ts | - | - | - | - | 🔲 pending |
| TlsCertificateTest.test.ts | - | - | - | - | 🔲 pending |
| TlsTest.test.ts | - | - | - | - | 🔲 pending |
| UtilsTests.test.ts | - | - | - | - | 🔲 pending |

## Status Legend
- 🔲 pending — not yet analyzed
- 🟡 in progress — currently running
- ✅ clean — 100/100 pass
- ⚠️ flaky — fails occasionally, fix identified
- ❌ broken — fails consistently
- 🔧 fixed — was flaky, now clean

## Failure Type Categories
- **TIMEOUT_BEFOREALL** — `beforeAll` hook exceeds timeout (cluster startup too slow)
- **TIMEOUT_TEST** — individual test exceeds timeout
- **READONLY** — write command routed to replica
- **CONNECTION** — client connection timed out or refused
- **ASSERTION** — test assertion failed (logic bug)
- **PORT_EXHAUSTION** — `os error 10048` address already in use
- **SUITE_CRASH** — test suite failed to run (setup error)

## Known Root Causes

### Cluster startup time (WSL1)
- `create_servers`: ~0.4s (fast)
- `create_cluster` (gossip convergence): **14-20s** (bottleneck)
- Gossip uses `valkey-cli cluster slots` polls with 1s sleep × 80 retries
- `valkey-cli` subprocess costs ~157-188ms on WSL1 vs ~5ms on native Linux
- Variance: p90=20s, occasional spikes to 35-40s under load

### NodeDiscoveryMode.Static `ReadOnly` errors
- 20% failure rate in isolation
- `NodeDiscoveryMode.Static` with multiple addresses uses `buffer_unordered` for connections
- Race condition: whichever node connects first becomes `nodes[0]`, which is trusted as primary
- Root cause not fully resolved

## Proposed Infrastructure Improvements

### 1. Reduce replica count where not needed (~3 min saved)
Files confirmed to not need replicas:
- `Compression.test.ts` — standalone(1,1→0) + cluster(3,1→0)
- `ClientSideCache.test.ts` — standalone(1,1→0) + cluster(3,1→0)
- `OpenTelemetry.test.ts` — cluster(3,1→0) × 3
- `AuthTest.test.ts` — standalone(1,1→0) + cluster(3,1→0)
- `ScanTest.test.ts` — cluster(3,1→0)
- `NapiClient.test.ts` — standalone(1,1→0) + cluster(3,1→0)
- `AddressResolver.test.ts` — cluster(3,1→0)
- `CompressionMaxSize.test.ts` — standalone(1,1→0) + cluster(3,1→0)

### 2. Shared clusters via globalSetup (~3.5 min saved)
- Start one standalone + one cluster once for all files
- Files that need special configs keep private clusters
- Requires `jest.config.ts` `globalSetup`/`globalTeardown`

### 3. Fix beforeAll timeouts (mitigation, not root cause fix)
- Timeouts of 20-40s are insufficient given p90=20s cluster startup
- Platform-agnostic issue (macOS shows same startup times)

## Results Log

| Date | File | Runs | Pass | Fail | Notes |
|---|---|---|---|---|---|
| 2026-06-19 | AuthTest.test.ts | 1 (full suite) | 48 | 0 | |
| 2026-06-23 | NodeDiscoveryMode.test.ts | 10 | 6 | 4 | READONLY 40% |
| 2026-06-23 | OpenTelemetry.test.ts | 10 | 8 | 2 | TIMEOUT_BEFOREALL 20% |
| 2026-06-23 | OpenTelemetry.test.ts | 10 | 10 | 0 | After instrumentation |
| 2026-06-24 | OpenTelemetry.test.ts | 10 | 10 | 0 | With timing logs |
