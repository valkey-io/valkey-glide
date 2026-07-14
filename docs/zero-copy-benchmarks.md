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
