# Zero-Copy Initiative — glide-core / redis-rs

Reducing per-value memory copies on the client hot path, on both the receive
(GET/MGET) and send (SET) sides, by teaching the vendored `redis-rs` to slice
the connection buffers instead of copying payloads.

- **Status:** implemented on a feature branch; benchmarked; all unit +
  integration tests passing.
- **Scope:** `glide-core/redis-rs/redis` (parser, `Value`, `Cmd`, connections),
  `ffi` (response arena, arg handling). No language-binding API changes
  required for the core win.

---

## 1. Motivation

Prior work (PRs #5492 / #5493) added a "zero-copy" buffered GET/SET API to the
Python-sync binding. Tracing the actual data path showed those paths were
**reduced-copy, not zero-copy**: they removed the arena allocation and the
binding-side (Rust→Python) copy, but the vendored `redis-rs` still copied every
payload at least once — because its contract forced owned buffers:

- **Receive:** the `combine` RESP parser builds `Value::BulkString(Vec<u8>)` by
  `to_vec()`-ing the payload out of the socket read buffer (one alloc + memcpy
  **per bulk string**), then the FFI copied that `Vec` again into the caller
  buffer/arena.
- **Send:** a `SET` value was copied up to three times after leaving the
  binding — into `Cmd.data`, into the packed RESP `Vec`, and into the framed
  write buffer — before reaching the kernel.

This initiative removes those copies structurally.

### Copy budget (256 KB value, before → after)

| Path | Before (userspace copies) | After |
|------|---------------------------|-------|
| GET / MGET receive (per value) | 2 (parser `to_vec` + arena/binding) | **1** (read buffer → caller) |
| GET via buffered API | 2 | **1** |
| SET send (borrowed slice) | 3 (+1 if compressed) | **1** |
| SET send (`arg_shared` / owned `Bytes`) | 3 | **0** |

The kernel↔userspace `recv`/`send` copy is the remaining floor and is out of
scope (it only disappears with `io_uring` registered buffers).

---

## 2. Design

Three phases, ordered by win-per-risk. Phases 1–2 are implemented; Phase 3 is
deliberately deferred (see §6).

### Phase 1 — Receive side: `Value::BulkString(Bytes)`

`Value::BulkString(Vec<u8>)` → `Value::BulkString(bytes::Bytes)`. `Bytes` is a
refcounted view, so bulk-string payloads can be **slices of the connection read
buffer** with no per-value allocation or memcpy.

The `combine` streaming parser cannot produce zero-copy slices — it only sees
borrowed `&[u8]` and must own partial frames that straddle socket reads. So the
async hot path (`ValueCodec`, used by `MultiplexedConnection`) uses a new,
purpose-built decoder:

1. **Scan** the read buffer for one complete top-level frame *without consuming
   anything* (walks type bytes + length headers only; payload bytes are skipped
   by length). Returns `None` if the frame is incomplete → wait for more data.
2. **Extract** the complete frame:
   - Frames ≤ 64 KB: one bulk `memcpy` into an owned `Bytes` (keeps the codec's
     `BytesMut` **unshared** so tokio keeps reusing its read allocation).
   - Frames > 64 KB: `split_to(len).freeze()` takes the allocation zero-copy
     (copying hundreds of KB costs more than losing one buffer's reuse).
3. **Parse** the `Value` tree by slicing bulk-string payloads directly out of
   that one frame `Bytes` — zero copies, zero allocations per element.

The sync `parse_redis_value` path keeps `combine` (copies into `Bytes`); it is
not the production hot path.

The **frame scanner is resumable** (`ScanState`: resume offset + a
remaining-children stack), persisted in `ValueCodec` across `decode` calls, so a
large multi-element frame arriving over many reads is scanned once
(O(elements)) instead of restarting from offset 0 every read
(O(reads × elements)).

**FFI arena** (`ResponseArena`) stores `Bytes` instead of `Vec<u8>`, so
non-buffered GET/MGET responses hand the refcounted read-buffer slice straight
to the binding — one copy end-to-end instead of two.

### Phase 2 — Send side: shared args + vectored writes

- `Cmd` args are stored as `StoredArg` = `Inline(offset) | Shared(Bytes) |
  Cursor`. Args larger than `SHARED_ARG_INLINE_MAX` (4 KB) — via the new
  `Cmd::arg_shared(Bytes)` or automatically in `write_arg` — are held
  out-of-line as refcounted `Bytes`, never copied into the command buffer.
- `SegmentedBytes` represents a packed command as a sequence of byte segments:
  framing + small inline args coalesce into contiguous segments; large shared
  payloads are their own zero-copy segments. `get_packed_segments()` is
  **byte-identical on the wire** to `get_packed_command()` (unit-tested).
- `MultiplexedConnection` splits its stream: `FramedRead` keeps the zero-copy
  decode; writes go through a new `VectoredSink` that queues segments and
  submits them with `poll_write_vectored` (512 KB backpressure watermark, ≤64
  iovecs/syscall). Payload segments go from the caller's allocation straight to
  the socket.
- The sync `Connection` and async single `Connection` gained the same vectored
  send path (`write_vectored` loops handling partial writes).

**Gating:** commands with no out-of-line payload
(`Cmd::has_out_of_line_args() == false`) take the original contiguous
pack-and-write path — the segmented/vectored machinery is only paid when a
large shared payload can actually skip a copy (avoids a measured +10%
small-command regression).

### Recycled buffer pool (`buf_pool`) — shared by both phases

Two hot paths briefly need an owned buffer of tens-to-hundreds of KB whose
lifetime is tied to a refcounted `Bytes`: the decoder's per-frame copy
(receive) and `write_arg`'s auto-share copy of large borrowed args (send).
Allocating these fresh per operation caused page-fault churn under pipelined
load (~37 faults/op at 64 KB, depth 16); the pool recycles the allocations so
the pages stay resident. Design points:

- **Recycling via drop hook:** buffers are handed out as
  `Bytes::from_owner(PooledBuf)`; the allocation returns to the pool only when
  the *last* `Bytes` clone/slice drops, so a buffer can never be reused while
  still referenced. No `unsafe`.
- **Sharded, not global:** the pool is **8 independent `Mutex<Vec<Vec<u8>>>`
  shards**. Threads are assigned a shard round-robin via a cached
  `thread_local` index — **no threads are created**; existing runtime/binding
  threads simply stop sharing one lock. A `Bytes` may be dropped on a
  different thread than allocated it; the buffer then parks in the dropping
  thread's shard, which is harmless (the pool is a best-effort cache, not an
  ownership structure). Rationale: a single process-global mutex on every
  >4 KB payload across *all* connections is a scalability cliff — measured to
  collapse ~8.6× under parallel load (see the multi-connection contention
  section in `zero-copy-benchmarks.md`).
- **Bounds:** copies ≤4 KB bypass the pool (allocator small-size classes
  handle them well); copies >1 MB also bypass it entirely — no pooled buffer
  could satisfy them without an immediate realloc, so popping one would only
  drain a warm buffer from mid-size traffic, and their allocations are never
  retained (a burst of huge values can't park large idle memory). Idle
  retention is capped at 8 buffers/shard → 64 MiB worst-case process-wide.
  In-flight buffers are unbounded by design (bounded by pipeline depth).
- **Poison recovery:** a poisoned shard lock only means another thread
  panicked mid push/pop; the `Vec` is structurally valid, so recycling
  continues (`unwrap_or_else(into_inner)`). Thread-teardown TLS access falls
  back to shard 0 instead of panicking.

---

## 3. Safety

- **Buffer pinning:** a `Bytes` slice keeps its whole underlying read chunk
  alive. Fine for request/response (dropped promptly), dangerous for retained
  values. `Value::detach_buffers()` deep-copies, and is applied at the
  client-side cache insert so cached values never pin network buffers. The FFI
  arena pins only until the binding frees the response.
- **Recursion guard:** the new iterative scanner enforces the same
  `MAX_RECURSE_DEPTH` as the recursive parser (depth = scan stack length),
  covered by codec-path tests for every RESP3 aggregate type.
- **Partial writes:** all vectored writers advance the `IoSlice` cursor until
  fully drained and treat a 0-byte write as `WriteZero`.
- **Wire-format equivalence:** `get_packed_segments` is asserted byte-identical
  to `get_packed_command` across simple/cursor/fenced/interleaved cases.

---

## 4. Benchmarks

Environment: valkey-server 8.1.3 on loopback; `perf stat`. Micro-benchmarks via
`glide-core/redis-rs/redis/benches/bench_zerocopy.rs`; e2e via the
`zc_e2e` / `zc_sync_set` examples. Baseline = upstream `main` at the branch
point.

### Receive — decode micro-bench (mean time, vs baseline)

| Shape | Change |
|-------|--------|
| Single GET 64 B / 1 KB / 16 KB / 256 KB | −56% / −37% / −9% / −95% |
| MGET 1000×64 B / 1000×1 KB / 100×16 KB | −71% / −71% / −51% |
| Pipeline 100×1 KB / 100×16 KB | −28% / ~flat |

### Receive — end-to-end (throughput / client CPU, vs baseline)

| Workload | Throughput | Client CPU |
|----------|-----------|------------|
| MGET 100×16 KB | +12–32% | **−60…−70%** |
| MGET 100×1 KB | **+1.9×** | −50% |
| Single GET 16 KB / 256 KB | parity | parity … −8% |

### Send — sync `SET` (instructions retired, vs baseline; low-noise single-thread)

| Value size | baseline | `arg` | `arg_shared` |
|------------|----------|-------|--------------|
| 256 KB | 629M | **381M (−39%)** | **103M (−84%)** |
| 16 KB | 642M | ~flat | 509M (−21%) |
| 1 KB (below threshold) | 979M | 953M (−2.6%) | inlined |

### Send — async `SET` (e2e, vs baseline)

| Workload | Throughput | Client CPU |
|----------|-----------|------------|
| SET 256 KB via `arg_shared` | **+3.2×** | **−78%** |
| SET 256 KB via plain `arg` | (noisy wall) | −42% instructions |
| SET 16 KB | +3–7% | ~parity |

Profiles confirm the parser `to_vec` and the send-side packing/encode copies
are gone; the remaining dominant cost is the kernel `recv`/`send` copy (the
Phase 3 floor).

---

## 5. Testing

- `redis` lib: 299 unit tests (incl. new segmented-packing and codec
  recursion-guard tests).
- Parser correctness: `partial_io_parse` (combine path) + new
  `codec_chunked_parse` quickcheck — the zero-copy codec must decode
  identically under arbitrary socket chunking.
- Connection integration (live server): `test_basic` 57 (sync send path),
  `test_async` 32 (async single-conn send path).
- `glide-core`: 150 lib tests. `ffi`: 60 tests incl. live-server e2e through
  the arena + vectored send.

---

## 6. Not done / follow-ups

- **Phase 3 (true zero-copy receive):** parse RESP payloads directly from the
  socket into pre-registered caller buffers, bypassing the read-buffer staging
  copy. High complexity (bifurcates the multiplexer read loop); Phase 1 already
  reaches the one-copy floor, so gated on a profile showing the staging copy
  matters.
- **Sync pipeline** (`ConnectionLike::req_packed_commands`) takes an
  already-packed `&[u8]` at the trait boundary, so it stays contiguous;
  vectoring it needs a trait-wide signature change.
- **Socket-listener bindings** (Node, python-async) still serialize responses
  into protobuf, which re-copies. The parser win still applies in front of it;
  full end-to-end benefit needs the protobuf-removal initiative.
- **Compression** forces an owned buffer on both sides regardless.

---

## 7. Commit map

| Area | Commits |
|------|---------|
| Benches (harness) | decode micro-bench, codec-path benches |
| Phase 1 receive | `Value::BulkString(Bytes)` + zero-copy frame codec; hybrid frame extraction; resumable scanner; FFI arena `Bytes` |
| Phase 2 send | `arg_shared` + `SegmentedBytes`; `VectoredSink`; FFI `arg_shared` wiring; auto-share + scratch reserve; sync/async-single vectored send; shared-arg gating |
