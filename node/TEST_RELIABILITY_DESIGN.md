# Node Test Suite Reliability & Speed — Design Proposals

## Background

This document summarizes findings from a flakiness audit of the Node test suite on Windows/WSL1 and proposes concrete improvements. The audit ran each of the 18 test files 10-100 times in isolation to measure pass rates and failure types.

### Audit Results Summary

| File | Pass Rate (isolation) | p90 | Notes |
|---|---|---|---|
| AddressResolver | 10/10 | 38s | clean |
| AuthTest | 10/10 | 53s | clean |
| ClientSideCache | 10/10 | 52s | clean |
| Compression | 10/10 | ~60s | clean |
| CompressionMaxSize | 10/10 | ~10s | clean |
| Dns | 10/10 | 13s | clean |
| GlideClient | 10/10 | 275s | clean |
| GlideClientInternals | 10/10 | 11s | clean |
| GlideClusterClient | 10/10 | 332s | clean |
| NapiClient | 10/10 | 43s | clean |
| NodeDiscoveryMode | 50/100 | 27s | FIXED — Rust bug |
| OpenTelemetry | 10/10 | 131s | clean |
| PubSub | 3/3 | 1352s | clean |
| ReadOnlyMode | 10/10 | 18s | clean |
| ScanTest | 10/10 | 93s | clean |
| TlsCertificateTest | 10/10 | 11s | clean |
| TlsTest | 10/10 | 65s | clean |
| UtilsTests | 10/10 | 10s | clean |

### Root Causes Identified

#### 1. Cluster startup time variability

Cluster startup (`wait_for_all_topology_views`) uses `valkey-cli cluster slots` polling with `time.sleep(1)` between retries. Each `valkey-cli` subprocess call takes ~157ms on WSL1. Gossip convergence requires ~7 retries per node × 6 nodes = ~14-20s typical, with spikes to ~40s under load.

This is the same on macOS (measured 17,407ms vs 17,269ms on Windows/WSL). The cluster startup time itself is not a WSL-specific problem.

**`beforeAll` timeouts that are tighter than the p90 startup time will fail intermittently on any platform.**

#### 2. NodeDiscoveryMode.Static routing bug (FIXED)

`NodeDiscoveryMode::Static` trusts `nodes[0]` as primary without running `INFO REPLICATION`. The Rust client used `buffer_unordered` to connect to all addresses concurrently, which yields results in completion order — not input order. If the replica connected faster, it became `nodes[0]` and received write commands, causing `ReadOnly` errors.

**Fix applied**: Changed `buffer_unordered` to `buffered` in `glide-core/src/client/standalone_client.rs`. This preserves input address order while maintaining concurrent connections. Pass rate improved from 50% to 20/20 (100%) on Windows.

---

## Proposal 1: Fix `beforeAll` Timeouts

**Status**: Ready to implement  
**Effort**: Low (4 file edits)  
**Risk**: None  

### Problem

Several `beforeAll` hooks create clusters with timeouts smaller than the measured p90 cluster startup time. Any run where startup takes longer than the timeout causes the entire test suite to fail.

### Measured data

| Config | p90 startup | Spike max |
|---|---|---|
| standalone + 1 replica | 7.4s | 7.4s |
| cluster 3 shards + 1 replica | 19s | ~40s |

### Changes needed

| File | Current timeout | Recommended | Reason |
|---|---|---|---|
| `Compression.test.ts` | `TIMEOUT = 30000` | `TIMEOUT = 120000` | Tests themselves take 55-62s; individual test timeout |
| `OpenTelemetry.test.ts` (GlideClusterClient beforeAll) | 40000 | 120000 | Cluster 3s+1r + client setup |
| `OpenTelemetry.test.ts` (GlideClient beforeAll) | 20000 | 60000 | Standalone+1r |
| `OpenTelemetry.test.ts` (parent span propagation beforeAll) | 40000 | 120000 | Cluster 3s+1r |

**Note**: These are not Windows workarounds. macOS shows identical cluster startup times (17,407ms macOS vs 17,269ms Windows/WSL). The timeouts are incorrect for all platforms.

---

## Proposal 2: Reduce Unnecessary Replica Count

**Status**: Ready to implement  
**Effort**: Low (8 file edits)  
**Risk**: None (confirmed no replica routing in these files)  
**Time saved**: ~3 minutes off full suite  

### Problem

Cluster startup with `replicaCount: 1` takes ~15-20s. With `replicaCount: 0` it takes ~2.5s. Many test files create clusters with 1 replica despite not using any replica-specific features (`readFrom`, `preferReplica`, `AZAffinity`, `readOnly`).

### Files confirmed safe to change (replicaCount 1 → 0)

| File | Change |
|---|---|
| `Compression.test.ts` | standalone(1,1→0) + cluster(3,1→0) |
| `ClientSideCache.test.ts` | standalone(1,1→0) + cluster(3,1→0) |
| `OpenTelemetry.test.ts` | cluster(3,1→0) ×3 describes |
| `AuthTest.test.ts` | standalone(1,1→0) + cluster(3,1→0) |
| `ScanTest.test.ts` | cluster(3,1→0) |
| `NapiClient.test.ts` | standalone(1,1→0) + cluster(3,1→0) |
| `AddressResolver.test.ts` | cluster(3,1→0) |
| `CompressionMaxSize.test.ts` | standalone(1,1→0) + cluster(3,1→0) |

### Files that must keep replicas

- `GlideClient.test.ts` — AZ affinity tests require replicas
- `GlideClusterClient.test.ts` — `wait` command, AZAffinity tests
- `NodeDiscoveryMode.test.ts` — `DiscoverAll` tests need replicas
- `ReadOnlyMode.test.ts` — by definition requires replica
- `TlsTest.test.ts` — TLS-specific, leave as-is
- `PubSub.test.ts` — pubsub topology, leave as-is

---

## Proposal 3: Jest `testRetries` for Flaky Tests

**Status**: Ready to implement  
**Effort**: Trivial (1 line in jest.config.ts)  
**Risk**: Minimal (hides intermittent failures, slightly longer CI on flaky runs)  

### Problem

Some tests (e.g. `NodeDiscoveryMode.Static`) are inherently timing-sensitive and may fail 1-5% of the time even after the root cause fix. `testRetries` automatically re-runs failed `it()` blocks before marking them as failed.

### Limitation

`testRetries` only retries `it()` blocks. It does **not** retry `beforeAll` hooks. For `beforeAll` timeout failures, Proposal 1 (fix timeouts) is required.

### Change

```ts
// jest.config.ts
const config: Config = {
    // ... existing config ...
    testRetries: process.env.CI ? 2 : 0,  // retry failing tests up to 2x in CI only
};
```

---

## Proposal 4: Shared Clusters via globalSetup

**Status**: Design phase  
**Effort**: Medium (jest config + 2 new files)  
**Risk**: Low  
**Time saved**: ~3.5 minutes off full suite  

### Problem

Each test file that needs a server creates its own cluster in `beforeAll` and tears it down in `afterAll`. With 16 files each paying ~15-20s startup cost, ~4 minutes of every test run is pure cluster startup overhead.

### Approach

Jest supports `globalSetup` and `globalTeardown` files that run once before/after all tests. A shared standalone + cluster can be started once and passed to all compatible files via environment variables (`STAND_ALONE_ENDPOINT`, `CLUSTER_ENDPOINTS` — already supported by all test files).

```ts
// jest.config.ts
globalSetup: './tests/globalSetup.ts',
globalTeardown: './tests/globalTeardown.ts',
```

```ts
// tests/globalSetup.ts
export default async function() {
    const standalone = await ValkeyCluster.createCluster(false, 1, 0, getServerVersion);
    const cluster = await ValkeyCluster.createCluster(true, 3, 0, getServerVersion);
    process.env.STAND_ALONE_ENDPOINT = standalone.getAddresses().map(a => a.join(':')).join(',');
    process.env.CLUSTER_ENDPOINTS = cluster.getAddresses().map(a => a.join(':')).join(',');
    // Store folders for teardown
    process.env._SHARED_STANDALONE_FOLDER = standalone.clusterFolder;
    process.env._SHARED_CLUSTER_FOLDER = cluster.clusterFolder;
}
```

### Files that can use shared clusters

All files that create `replicaCount: 0` clusters (after Proposal 2) can use the shared clusters: Compression, ClientSideCache, OpenTelemetry, AuthTest, ScanTest, NapiClient, AddressResolver, CompressionMaxSize.

### Files that still need private clusters

Files requiring specific topology (replicas, TLS, special configs): GlideClient, GlideClusterClient, NodeDiscoveryMode, ReadOnlyMode, TlsTest, PubSub.

---

## Proposal 5: Reduce Integration Tests / Add Unit Tests

**Status**: Discussion  
**Effort**: High (ongoing)  
**Risk**: Medium (can hide real issues)  

### Assessment

The Node client is a thin TypeScript layer over a Rust FFI binary. Most business logic lives in the Rust core (`glide-core`). The TypeScript layer handles:
- Argument serialization to protobuf
- Response deserialization
- Configuration validation
- Client lifecycle management

**What can be unit tested** (no server needed):
- Configuration validation (e.g. `validateCompressionConfiguration`)
- Error type mapping
- Batch/transaction building (command serialization)
- `parseEndpoints`, `intoString`, `checkClusterResponse` utilities

`GlideClientInternals.test.ts` (9s, no server) already does this pattern and is the right template.

**What cannot be meaningfully mocked**:
- FFI boundary correctness (Rust ↔ TypeScript protobuf encoding)
- Connection handling and retry logic (lives in Rust)
- Cluster topology routing
- PubSub behavior
- Any server-side validation

**Recommendation**: Add unit tests for pure TypeScript logic incrementally. Do not reduce existing integration test coverage — the integration tests catch real bugs (the `NodeDiscoveryMode` ordering bug was found this way).

---

## Implementation Priority

| Priority | Proposal | Status |
|---|---|---|
| 1 | Fix Rust `buffer_unordered` → `buffered` | ✅ Done |
| 2 | Fix `beforeAll` timeouts (Proposal 1) | Ready to implement |
| 3 | Reduce replica count (Proposal 2) | Ready to implement |
| 4 | Add `testRetries: 2` in CI (Proposal 3) | Ready to implement |
| 5 | Shared globalSetup clusters (Proposal 4) | Design phase |
| 6 | Add unit tests (Proposal 5) | Ongoing |

## Estimated Impact

| Change | Suite time reduction | Flakiness reduction |
|---|---|---|
| Rust ordering fix | 0 | NodeDiscoveryMode 100% reliable |
| Fix timeouts | 0 | Eliminates beforeAll timeout failures |
| Reduce replicas | ~3 min | Reduces startup variance |
| globalSetup sharing | ~3.5 min | Reduces startup variance |
| testRetries | 0 | Absorbs remaining 1-5% flakiness |
| **Combined** | **~6.5 min** | **Near-zero flakiness** |

Current full suite: ~22 min → Estimated after all changes: **~15-16 min**.
