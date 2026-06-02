# Pipeline buffer backpressure — investigation & fix (#5446)

Date: 2026-06-02
Status: implemented on branch `fix/pipeline-send-timeout-liveness-5446`
Component: `glide-core/redis-rs/redis/src/aio/multiplexed_connection.rs`

## Summary

Issue [#5446](https://github.com/valkey-io/valkey-glide/issues/5446) reported that
the hardcoded pipeline buffer size of 50 limits concurrency. It was closed as
"benchmarking showed performance degradation" when the buffer was raised.

Investigation shows the real problem is **not throughput** and the closing
benchmark was misleading. Under sustained backpressure the 50-slot channel,
combined with a fixed 100 ms send-timeout, makes a **live connection fail
commands** with `FatalSendError`. The fix repairs the send-timeout to be
liveness-aware; the buffer size then becomes a non-issue and stays at 50.

## Background: the data path

A multiplexed connection has three serial admission stages:

1. **Pipeline channel** — a bounded `mpsc` (capacity 50) between request
   producers and the single writer task.
2. **Socket write path** — the codec/OS TCP send buffer; `poll_ready` returns
   `Pending` when it is full. Bounded in *bytes* (≈ bandwidth-delay product).
3. **Inflight limit** — a glide-core semaphore (default 1000) above both.

The single writer task (`receiver.forward(sink)`) drains the channel, writes to
the socket, and reads responses. The channel only fills when the socket write
path is blocked, i.e. drain is slower than fill — high latency and/or large
payloads.

A send to the channel was wrapped in a `min(timeout, 100ms)` timeout (added in
[#5715](https://github.com/valkey-io/valkey-glide/issues/5715) to avoid blocking
forever on a half-open TCP connection). A blocked send → `FatalSendError`.

## Investigation

A microbenchmark (`bench_pipeline_buffer_sweep`, ignored by default) drives the
real `Pipeline` against a `MockServerSink` that injects a configurable RTT and a
byte-bounded in-flight window, with no sockets or JVM in the path. It sweeps
buffer × payload × latency and — critically — **counts successes vs failures**.

Key results (500 concurrent commands, window = 2 MB ÷ payload):

| payload | latency | buffer 50 | buffer 1000 |
|---|---|---|---|
| 64 B | 10 ms | 500 ok / 0 fail | 500 ok / 0 fail |
| 4 KB | 10 ms | 500 ok / 0 fail | 500 ok / 0 fail |
| 1 MB | 0 ms | 500 ok / 0 fail | 500 ok / 0 fail |
| 1 MB | 10 ms | **75 ok / 425 fail** (≈455 ms) | 500 ok / 0 fail (≈2980 ms) |

### Findings

1. Buffer size is irrelevant for small/medium payloads and fast links.
2. The 1 MB + latency regime is where the 50-slot buffer fails ~85 % of
   commands. All failures are `FatalSendError` — the 100 ms send-timeout firing
   on a live, draining connection.
3. **The "performance degradation" was a measurement artifact.** Wall-clock
   *rises* with buffer size (455 → 2980 ms) only because the small buffer
   "finishes" fast by failing most commands. Counting only successes, ~2980 ms
   is the honest window-limited cost of delivering all 500 × 1 MB; the small
   buffer is not faster, it is broken. (The same trap appeared in our first
   benchmark pass — `let _ = handle.await` silently counted failures as
   completions.)

## Root cause

The fixed 100 ms send-timeout cannot tell a *dead* connection (no progress)
from a *slow-but-live* one (progress, just slow). Both leave the channel full,
so legitimate backpressure is misclassified as connection death.

## Options considered

- **A — Raise/align the buffer with the inflight limit (1000).** Eliminates the
  failures but holds up to `1000 × payload` per connection (≈1 GB for 1 MB
  values). Trades failures for a memory cliff. A byte-bounded buffer would cap
  memory but is a larger change.
- **B — Make the send-timeout liveness-aware (chosen).** Fixes the actual
  defect, keeps the 50-slot buffer, no extra memory.

## Solution (B)

The writer publishes a shared monotonic `progress` counter, bumped when it
**drains a command into the sink** (a freed slot) or **receives a response**.
A producer waiting for channel capacity polls for a slot in liveness ticks:

- slot acquired → send;
- channel closed → `FatalSendError`;
- tick elapsed, overall request `timeout` exceeded → timeout error;
- tick elapsed, **no progress since the tick began** → dead → `FatalSendError`
  (preserves #5715 fast detection);
- tick elapsed, progress made → backpressure, keep waiting.

A subtlety found during implementation: the liveness signal must be bumped on
`start_send`, not only on response receipt. The futures `Forward` combinator
starves `poll_flush`/`poll_read` whenever the stream always has items (exactly
the backpressure case), so a response-only signal stays frozen at 0. "Writer
drained a command" is the correct signal — it directly means a slot freed.

`Pipeline::new_with_buffer_size` is added (default unchanged at 50), used by the
benchmark and available as an optional tuning knob mirroring upstream redis-rs.

## Validation

| Scenario | Test | Result |
|---|---|---|
| Dead connection (never drains) | `test_pipeline_send_timeout_when_sink_stalls` (#5715) | still fails fast ✅ |
| Live-but-slow backpressure | `test_backpressure_does_not_fail_live_commands` (#5446) | 200 ok / 0 fail ✅ |
| Buffer capacity wiring | `test_new_with_buffer_size_respects_capacity` | ✅ |
| Harness self-check | `test_mock_server_sink_responds_to_all_commands` | ✅ |

Sweep after the fix: the 1 MB/10 ms row is **500 ok / 0 fail for every buffer
size**, converging to ~2970 ms — buffer size no longer affects correctness or
timing. 251 redis-rs lib tests pass; `glide-core` compiles; no clippy warnings
in the changed file.

## Known limitations / follow-ups

1. **High-RTT links (RTT > the 100 ms liveness tick).** Under sustained
   backpressure a quiet ~RTT window with no drain could trip the dead-path. The
   `start_send` signal mitigates this (the writer drains quickly at first), but
   a fully robust version should tie the no-progress threshold to the request
   timeout — which tensions against #5715's test asserting detection within
   ~100 ms. Worth a follow-up once a policy is agreed.
2. **Worst-case 2× timeout.** Send may wait up to `timeout`, then recv waits up
   to `timeout`. Cleanly fixed by sharing one deadline across both stages; kept
   out of this minimal change.
3. **`Forward` starves response reading under sustained backpressure.** Noticed
   during investigation: responses/oneshots are not processed while the channel
   stays full. Pre-existing and orthogonal to #5446, but worth its own issue.

## Recommendation

Reopen #5446 with these findings; the closing rationale ("performance
degradation") is contradicted by the success/failure data. Ship B.
