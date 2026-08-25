# Zero-Copy Initiative — Benchmark Analysis

Measured impact of the reduced-copy receive path and the vectored/zero-copy
send path. **Terminology:** the receive side is *reduced-copy* (reaches the
one-copy floor: kernel read buffer → caller); the send side via `arg_shared` is
*true zero-copy* (owned `Bytes` → socket, no userspace copy); plain `arg` send
is reduced to one copy.

## Methodology

- **Baseline:** upstream `main` at the branch point, built identically.
- **Server:** valkey-server 8.1.3, loopback (127.0.0.1). Loopback means the
  kernel `send`/`recv` copy and TCP dominate wall-clock at large value sizes, so
  **instructions retired** (via `perf stat`) is reported as the primary
  *client-CPU* signal where wall-clock is noisy.
- **Harnesses (in `glide-core/redis-rs/redis`):**
  - Micro: `benches/bench_zerocopy.rs` (criterion) — decodes RESP frames through
    the production `ValueCodec` path.
  - E2e receive/send: `examples/zc_e2e.rs` — pipelined GET/MGET/SET through a
    real `MultiplexedConnection`.
  - E2e sync send: `examples/zc_sync_set.rs` — single blocking connection, tight
    SET loop (low-noise, single-threaded).
- **Repetition:** 3+ alternating baseline/modified runs; high-variance cells are
  flagged.

## Receive — decode micro-benchmark (`ValueCodec`)

Mean decode time vs baseline (negative = faster). The receive change replaces a
per-bulk-string `Vec` alloc + `memcpy` with a slice of one frame buffer.

| Shape | Δ time |
|-------|--------|
| single GET 64 B | −47…−56% |
| single GET 1 KB | −31…−37% |
| single GET 16 KB | −9…−16% |
| single GET 256 KB | −70…−95% (high variance) |
| MGET 1000×64 B | −71% |
| MGET 1000×1 KB | −71…−76% |
| MGET 100×16 KB | −51…−59% |
| pipeline 100×1 KB | −28% |
| pipeline 100×16 KB | ~flat…−36% (high variance) |

**Reading it:** multi-element responses (MGET) win most — one alloc+copy per
element is eliminated, so allocator pressure drops sharply. Single small GETs
win from the removed allocation; large single GETs are dominated by the one
remaining copy so the % swings with noise.

## Receive — end-to-end (`MultiplexedConnection`)

Throughput and client CPU vs baseline (via `perf stat task-clock`).

| Workload | Throughput | Client CPU |
|----------|-----------|------------|
| MGET 100×1 KB | **+1.9×** | −50% |
| MGET 100×16 KB | +12…+32% | **−60…−70%** |
| single GET 16 KB | parity | parity |
| single GET 256 KB | parity | −0…−8% |

MGET is the headline: at 100×1 KB the client is CPU-bound on
per-element alloc/copy, so removing it nearly doubles throughput. Single GET is
server/loopback-bound, so client-CPU savings don't move throughput.

## Send — sync SET (`zc_sync_set`, instructions retired)

Single-threaded, low-noise. Instruction count is the clean client-CPU signal.

| Value size | baseline | plain `arg` | `arg_shared` |
|------------|----------|-------------|--------------|
| 256 KB | 629 M | **381 M (−39%)** | **103 M (−84%)** |
| 16 KB | 642 M | ~flat | 509 M (−21%) |
| 1 KB (< 4 KB inline threshold) | 979 M | 953 M (−3%) | inlined (n/a) |

- `arg` (borrowed slice): payload copied **once** into an owned `Bytes`, then
  vectored to the socket — the packing + framed-buffer copies are gone (3 → 1).
- `arg_shared` (caller owns `Bytes`): **zero** userspace payload copies.
- Below the 4 KB threshold the segmented/vectored path is gated off, so small
  commands take the original contiguous path (no regression).

## Send — end-to-end SET (`zc_e2e`)

| Workload | Throughput | Client CPU |
|----------|-----------|------------|
| SET 256 KB via `arg_shared` | **+3.2×** | **−78%** |
| SET 256 KB via plain `arg` | wall noisy | −42% instructions |
| SET 16 KB | +3…+7% | ~parity |

## What does NOT improve (honest limits)

- The kernel↔userspace `recv`/`send` copy remains — it is the one-copy floor and
  only disappears with `io_uring` registered buffers (out of scope).
- Single small GET/SET end-to-end throughput is server/loopback-bound; the win
  is client-CPU, visible under CPU-bound multiplexed load, not in these
  round-trip numbers.
- Socket-listener bindings (Node, python-async) re-serialize responses into
  protobuf, re-copying; the parser win still applies in front of that layer but
  the end-to-end binding win awaits protobuf removal.
- Compression forces an owned buffer on both sides.

## Reproduction

```bash
cd glide-core/redis-rs/redis
# micro
cargo bench --bench bench_zerocopy --features tokio-comp -- codec
# e2e (needs a server on :6399)
cargo build --release --example zc_e2e --example zc_sync_set --features tokio-comp
perf stat -e task-clock,instructions -- \
  ./target/release/examples/zc_e2e 6399 mgetv 16384 20000 64
perf stat -e instructions -- \
  ./target/release/examples/zc_sync_set 6399 arg_shared 262144 20000
```

## Real-network validation (ElastiCache, 2026-07-15)

All loopback results above were re-validated over a real network hop: single-primary
ElastiCache Valkey 8.1 (`cache.r7g.4xlarge`, cluster-mode disabled) with same-AZ EC2
clients — x86 (`c7i.8xlarge`) and ARM/Graviton (`c7gn.16xlarge`) — in us-west-2a.
TCP_NODELAY enabled (matching glide-core's production default). Measurement: persistent
pipelined workers (no per-window allocation in the harness), 3 alternating reps per cell,
`perf stat` instructions + page-faults, per-op p50/p95/p99 latency. Network saturation was
verified explicitly: no run exceeded instance or cache bandwidth allowances; large-value
cells sit at EC2's ~5 Gbps per-TCP-flow limit (confirmed by a multi-flow scaling probe),
which is why client CPU per op — not throughput — is the primary KPI.

Profiling over the real hop exposed an allocation/page-fault-churn pattern that loopback
tuning had missed (fresh large buffers alive across the pipeline window). It was fixed with
a recycled buffer pool backing large transient copies (`buf_pool.rs`) plus copy-always frame
extraction; the numbers below include those fixes.

### Client CPU (instructions/op, baseline → this work), x86 / ARM

| workload | x86 | ARM |
|---|---|---|
| MGET 100x64KB, depth 16 | **−62%** | **−31%** |
| SET 256KB plain, depth 16 | **−22%** | **−32%** |
| SET 256KB `arg_shared`, depth 128 | **−40%** | **−45%** |
| GET 64KB, depth 16 | parity | **−12..15%** |
| GET/SET ≤16KB | parity (±5%) | parity to −16% |

Page faults/op drop 1–3 orders of magnitude on all large-value cells (e.g. x86 MGET 64KB:
1938 → 18.7; ARM baseline faults even at 64KB GET, which the pool eliminates — the win is
larger on Graviton).

### Latency (p50/p95/p99)

- p50 is flat on both architectures: at these sizes the wire transfer dominates the median
  (a 256 KB value ≈ 3.4 ms of transfer vs ~10 µs of CPU saved).
- Send-path tails improve: p99 −50..170 µs at 256 KB (both arches) — pooling removes
  allocation/fault stalls from the submit path.
- Unpipelined depth-1: no latency/throughput change for small/medium values (RTT-bound, by
  design); `arg_shared` SET 64KB is the one depth-1 win (+8–15% throughput, −10..20 µs p50).

### Honest costs and corrections

- An earlier revision regressed sub-4KB commands 10–22% at extreme pipeline depth (d128,
  ~300k+ ops/s on a single connection). Root causes (profile-verified): per-command
  container allocation, `Bytes::from(Vec)` shrink-realloc, and segment bookkeeping. Fixed
  (inline-first `SegmentedBytes`, `Bytes::from_owner`, `SendBuf::Contiguous` fast path) and
  re-verified with a 41-cell × 3-rep matrix on both architectures: **every small/medium
  cell is at parity with baseline** (some positive, e.g. GET 512B d128 throughput +11–13%).
  A "+9% tiny SET CPU" figure quoted in earlier revisions was a measurement artifact
  (bimodal run-to-run variance present in baseline too) and is retracted.
- Throughput only improves where CPU was the binding constraint (e.g. SET 256KB depth 1:
  +37% x86, +15..22% ARM); flow-capped cells are unchanged by design.

## Multi-connection pool contention (ElastiCache + microbench, 2026-07-19)

**Question:** the recycled buffer pool originally used one process-global
`Mutex<Vec<Vec<u8>>>`, taken by every payload >4 KB (send args and receive
frames) across all connections. Does it contend under multi-connection load,
and does the 8-shard thread-affine pool fix it?

**Method:** two builds byte-identical except `buf_pool.rs` (`sharded` vs
`global`); same infra as the real-network validation (c7i.8xlarge client +
same-AZ `cache.r7g.4xlarge`, us-west-2a). Harnesses: `zc_multiconn`
(C independent `MultiplexedConnection`s × depth-16 persistent workers) and
`zc_poolstress` (pure pool build/drop, no I/O). 3 alternating reps/cell,
`perf stat` (task-clock, instructions, context-switches).

### Live ElastiCache (GET/SET × 8 KB/64 KB × 1–32 connections, depth 16)

- **Throughput identical in every cell** (Δ < 0.1%): all ≥8-connection cells
  sit on the ~12.4 Gbps instance network plateau, so the wire — not the pool
  lock — is the ceiling.
- **Client CPU/op: sharded −3..5% at 16–32 connections** (8 KB GET @32 conns:
  20.0 µs vs 21.2 µs), with instructions/op flat → the recovered time is
  lock-wait, not executed work. Single-connection cells: parity.

### Pure pool microbenchmark (no I/O — worst case, 8 KB payload)

| threads | sharded M ops/s | global M ops/s | global CPU ns/op | sharded ns/op |
|---:|---:|---:|---:|---:|
| 1 | 3.78 | 3.81 | 262 | 265 |
| 4 | 6.48 | 2.01 | 1 959 | 515 |
| 8 | 12.10 | **1.41** | 4 673 | 527 |
| 16 | 6.57 | 1.50 | 8 956 | 1 904 |
| 32 | 5.34 | 1.62 | 18 672 | 3 731 |

The global mutex collapses beyond ~4 threads: throughput drops *below* its
single-thread rate, CPU/op inflates ~70×, and context switches explode to
~165 K per M-ops (futex convoy). The sharded pool scales to ~12 M ops/s and
degrades gracefully.

### Honest framing

The global-lock collapse point (~2–4 M pool-ops/s) is roughly 10× beyond what
any real network workload drives on this hardware (max observed live rate:
~190 K ops/s over the wire, ~380 K/s loopback — where the variants are
indistinguishable in throughput). Sharding is justified as removing a hard
scalability cliff (faster NICs / bigger instances / more runtime threads move
workloads toward it) plus the measured −3..5% CPU/op at high connection
counts, with no regression in any cell (worst: +0.9% CPU single-threaded,
within noise). No new threads are created by the sharding — see the design
doc.
