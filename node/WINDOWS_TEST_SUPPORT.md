# Windows Node.js Test Support

This document describes the changes made to enable the Node.js integration test suite to run on Windows via WSL.

## Background

The Node.js integration tests spin up real valkey-server instances using `utils/cluster_manager.py`. On Windows, valkey-server is not available natively — it runs inside WSL (Windows Subsystem for Linux). This required routing all server lifecycle calls through WSL and handling several Windows-specific issues.

## Startup Benchmark

Measured on Windows WSL1 (10 runs each) to determine appropriate test timeouts:

| Configuration | min | median | p90 | p99 | max |
|---|---|---|---|---|---|
| standalone, 0 replicas | 0.4s | 0.4s | 0.4s | 0.5s | 0.6s |
| standalone, 1 replica | 5.4s | 6.3s | 6.5s | 7.3s | 7.4s |
| cluster 3 shards, 0 replicas | 2.5s | 2.5s | 2.6s | 3.4s | 3.5s |
| cluster 3 shards, 1 replica | 13.8s | 16.0s | 19.0s | 19.0s | 19.0s |

Key observations:
- All replicas report `master_link_status:up` and `master_sync_in_progress:0` immediately when `cluster_manager.py` exits — replica sync completes within the startup window
- The cluster startup time itself (15-19s for 3 shards + 1 replica) is the bottleneck
- `beforeAll` timeout failures (`Compression` at 30s, `OpenTelemetry` at 40s) occurred when these suites ran later in the full suite sequence, suggesting WSL overhead compounds under sustained load
- Exact overhead under load was not measured; timeouts were set conservatively based on observed failures

Benchmark script: `utils/startup_benchmark.py`

## Changes

### `utils/TestUtils.ts`

Three additions:

**1. WSL routing** — detects Windows and rewrites the Python script path from a Windows path to a WSL mount path, then prefixes the command with `wsl`:

```ts
const isWindows = process.platform === "win32";

function toWslPath(p: string): string {
    return p
        .replace(/^([A-Za-z]):/, (_, d) => `/mnt/${d.toLowerCase()}`)
        .replace(/\\/g, "/");
}

const wslScriptPath = isWindows ? toWslPath(PY_SCRIPT_PATH) : PY_SCRIPT_PATH;
```

Applied in both `createCluster` and `close`:
```ts
const [cmd, cmdArgs] = isWindows
    ? ["wsl", ["python3", wslScriptPath, ...commandArgs]]
    : ["python3", [PY_SCRIPT_PATH, ...commandArgs]];
```

**2. Replica readiness polling** — after cluster creation on Windows with replicas, polls each node via `INFO replication` over a raw TCP/RESP connection until the replica reports `master_link_status:up` and `master_sync_in_progress:0`. In practice replicas are already synced when `cluster_manager.py` exits, so this returns immediately. It serves as a safety net for cases where sync may lag under heavy load:

```ts
async function waitForReplicasReady(
    addresses: [string, number][],
    timeoutMs = 15000,
): Promise<void>
```

Called after `createCluster` resolves:
```ts
.then(async (cluster) => {
    if (isWindows && replicaCount > 0) {
        await waitForReplicasReady(cluster.getAddresses());
    }
    return cluster;
})
```

### `node/rust-client/package.json`

Removed `$npm_config_build_flags` from all four build scripts (`build:dev`, `build:benchmark`, `build:release`, `build:release:gnu`). This bash-style variable is not expanded on Windows cmd/PowerShell and caused cargo to error with `unexpected argument '$npm_config_build_flags'`.

### `node/tests/Compression.test.ts`

Increased `beforeAll` timeout: `30000` → `120000` ms.

The Compression test creates two clusters (standalone + cluster-mode, both with 1 replica). When running after many preceding test suites, WSL is under load and the 30s default was insufficient. Based on the benchmark (cluster p99 = 19s under idle, 2-3x under load) 120s provides adequate headroom.

### `node/tests/OpenTelemetry.test.ts`

Two `beforeAll` timeout increases:

| Describe block | Before | After | Reason |
|---|---|---|---|
| `OpenTelemetry GlideClusterClient` | 40000 ms | 120000 ms | Creates cluster 3s+1r; p99=19s idle, higher under load |
| `OpenTelemetry GlideClient` | 20000 ms | 60000 ms | Creates standalone+1r; p99=7.4s idle, higher under load |

## System Configuration Required

### WSL with valkey-server

Valkey must be installed inside WSL:
```bash
wsl
cd /tmp
wget https://github.com/valkey-io/valkey/archive/refs/tags/8.0.1.tar.gz
tar xzf 8.0.1.tar.gz && cd valkey-8.0.1
make && sudo make install
```

### Windows TCP tuning (reduces port exhaustion)

Run as Administrator in PowerShell, then reboot:
```powershell
# Reduce TIME_WAIT from 4 minutes to 15 seconds
Set-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Services\Tcpip\Parameters" -Name "TcpTimedWaitDelay" -Value 15 -Type DWord

# Expand ephemeral port range
netsh int ipv4 set dynamicport tcp start=10000 num=55535
```

This is required because the full test suite starts and stops ~16 clusters sequentially. Without it, Windows holds ports in TIME_WAIT long enough to cause `os error 10048` (address already in use) on later test files.

### Node.js PATH

If `node` and `npm` are not on PATH after installation:
```powershell
[Environment]::SetEnvironmentVariable("Path", "C:\Program Files\nodejs;" + [Environment]::GetEnvironmentVariable("Path", "Machine"), "Machine")
```

## Test Results

| | Before | After |
|---|---|---|
| Test suites passing | 0 (no WSL routing) | 15 of 15 |
| Tests passing | — | 1900+ |
| Suites skipped | — | 2 (DNS, IAM — require env vars) |
