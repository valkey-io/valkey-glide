# Task 1B report

## Result

Async refresh inputs now preserve raw versus ready provenance. Raw inputs are resolved exactly once before refresh state lookup; ready inputs bypass the resolver. Prepared addresses are deduplicated before task creation, so equivalent raw and ready values share one refresh task. Initial seed lookup returns raw provenance when no direct or canonical IP mapping exists, including when an OS socket endpoint is available.

## TDD evidence

- RED: `mixed_raw_and_ready_refresh_prepares_once_and_deduplicates` initially failed because two equivalent prepared addresses created two tasks.
- GREEN: after deduplicating prepared final addresses, the focused test passed and asserted one resolver call, one notifier, and the final `resolved-node:6381` dial target.

## Verification

- `cargo test --manifest-path glide-core/redis-rs/redis/Cargo.toml --lib --features cluster refresh_task_resolution_tests::mixed_raw_and_ready_refresh_prepares_once_and_deduplicates` — passed.
- `cargo check --manifest-path glide-core/redis-rs/redis/Cargo.toml --lib --features cluster` — passed.
- `cargo fmt --manifest-path glide-core/redis-rs/redis/Cargo.toml --all` — passed.
- `git diff --check` — passed.

The full library test suite was not rerun in this focused handoff.

## Parallel-test diagnosis

- RED (fresh full-lib run): `concurrent_refresh_requests_share_one_task_and_both_complete` and `mixed_raw_and_ready_refresh_prepares_once_and_deduplicates` intermittently timed out waiting for `POISON_CONNECT_STARTED`/completion when lib tests ran in parallel.
- Root cause: these tests shared process-global `Notify` and `Semaphore` gates in `RecordingConnection`. The mixed test could consume the concurrent test's notification, and permits released by one test remained available to the next, allowing its gated connection to run before the expected wait. The production refresh lifecycle was not implicated; the focused module run passed.
- GREEN: gated tests now serialize through `GATED_TEST_LOCK` and drain stale semaphore permits before each run. The focused module suite passed repeatedly (20 runs with `--test-threads=4`).
