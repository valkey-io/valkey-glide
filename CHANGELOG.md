# Changelog

## Pending 2.6

### Fixes

* Java: fix(java): resolve flaky timeout in migrate_cluster_mode_basic test ([#6746](https://github.com/valkey-io/valkey-glide/pull/6746))
* Core/FFI: Strip brackets from IPv6 address literals in `create_client_from_uri` so bracketed hosts (e.g. `redis://[::1]:6379`) resolve correctly. `url::Url::host_str()` returns the literal with its surrounding brackets (`[::1]`), which `tokio::net::lookup_host` cannot resolve, causing connections to IPv6 literals to hang until the connect timeout. The URI parser now uses the unbracketed canonical form via `Ipv6Addr::to_string()`.

* Python: Make async pipe transport fork-safe. After `fork()`, the flush thread is gone but `OnceLock` prevented reinitialization, causing commands in forked child processes (e.g. PySpark workers) to hang indefinitely. Now detects fork via PID comparison and reinitializes the pipe. Using a parent's client object in a forked child now raises `ClosingError` on command paths and skips the FFI call on `close()`, instead of silently hanging. ([#6673](https://github.com/valkey-io/valkey-glide/issues/6673))
* Core/FFI: Percent-decode userinfo in `create_client_from_uri` so credentials containing URI-reserved characters (`@`, `:`, `/`, `?`, `#`, `%`, `+`, space, non-ASCII) authenticate correctly ([#6659](https://github.com/valkey-io/valkey-glide/issues/6659))
* Core/All: Buffer pending cluster requests during reconnect instead of failing immediately. When a circular MOVED redirect triggers a reconnect, requests arriving during the recovery window are now queued and retried transparently once reconnection completes. A new `recovery_requests_queue_size` option (default: 1000) controls the queue depth. ([#6640](https://github.com/valkey-io/valkey-glide/pull/6640))
* Python: fix trio hang on free-threaded builds by waking `_CompatFuture` waiters from the owning trio thread ([#6685](https://github.com/valkey-io/valkey-glide/pull/6685))
* Python: Fix trio pub/sub BusyResourceError from duplicate shared-pipe reader ([#6605](https://github.com/valkey-io/valkey-glide/pull/6605))
* Java: Make the automatic JVM shutdown hook non-destructive so a command issued from a user's own shutdown hook is no longer rejected with `ClosingException: Client is shutting down`. The JVM runs all shutdown hooks concurrently, and GLIDE's hook previously tore the client down and blocked new requests, racing with (and aborting) legitimate work such as persisting state before exit. Deterministic teardown remains available via the client's `close()`. ([#4809](https://github.com/valkey-io/valkey-glide/issues/4809))
* Core: Fix native panic for setex psetex and setnx commands ([#6551](https://github.com/valkey-io/valkey-glide/pull/6551))
* Core/FFI: fix(ffi): forward Disconnection push notifications past the malformed-frame guard ([#6543](https://github.com/valkey-io/valkey-glide/pull/6543))
* CI: Run `test-release` in `pypi-cd.yml` when only one package is published manually, so a skipped sibling publish job no longer causes post-publish validation to be skipped entirely ([#6542](https://github.com/valkey-io/valkey-glide/pull/6542))
* Core/FFI: fix(ffi): prevent pub/sub DoS from malformed server push frames ([#6530](https://github.com/valkey-io/valkey-glide/pull/6530))
* Python: Restore `BaseClient.__aenter__` return type to `Self` (from the widened `"BaseClient"` introduced in 2.5.0). Entering the async context manager (`async with await GlideClusterClient.create(...) as client`) now preserves the concrete subclass for static type checkers, matching `create()`. ([#6531](https://github.com/valkey-io/valkey-glide/issues/6531))
* Core: fix(core): enforce RESP3 recursion-depth guard for all aggregate types ([#6477](https://github.com/valkey-io/valkey-glide/pull/6477))
* Core: Update `anyhow` to 1.0.103 to fix RUSTSEC-2026-0190, an unsoundness advisory in `anyhow::Error::downcast_mut()` that can trigger undefined behavior ([#6364](https://github.com/valkey-io/valkey-glide/pull/6364))
* Go: Remove `.gitignore` from the released module so consumers who commit `vendor/` keep the generated artifacts (`internal/protobuf/*.pb.go`, `rustbin/**`, `lib.h`) ([#6441](https://github.com/valkey-io/valkey-glide/pull/6441))

### Changes

* Java: Add `GlideString.asReadOnlyByteBuffer()` for zero-copy, read-only access to binary payloads  ([#6600](https://github.com/valkey-io/valkey-glide/issues/6600))
* Core: Zero-copy receive path for GET/MGET ([#6559](https://github.com/valkey-io/valkey-glide/pull/6559))
* Go: Expose `inflightRequestsLimit` configuration via `WithInflightRequestsLimit`, bringing the Go client to parity with Java, Python, and Node ([#6385](https://github.com/valkey-io/valkey-glide/issues/6385))
* Core, Java, Python, Node, Go: Add client-instance pooling and isolated execution scopes. Pools eliminate multiplexer contention under high concurrency; scopes provide dedicated connections for WATCH/MULTI/EXEC and CLIENT TRACKING. All languages share a unified Rust implementation via `send_scope_command()` and `release_client_async()`. Pool release resets state (DISCARD + SELECT). Scopes inherit parent's current database, credentials, and compression. Circuit breaker and inflight limits enforced. Abandon detection reclaims leaked borrows after configurable timeout (default 5 min, skips blocking commands, 0 to disable). ([#6338](https://github.com/valkey-io/valkey-glide/pull/6338))
* Python: Add Python 3.14t free-threaded support to the test matrix. ([#5445](https://github.com/valkey-io/valkey-glide/issues/5445))
* Core/FFI: Add `command_with_route_info` FFI entrypoint, accepting routing as a `RouteInfo` C-struct pointer instead of protobuf-encoded bytes — the same mechanism `batch()` already uses. Existing `command`, `command_with_buffer`, `command_with_buffers`, and `invoke_script` are unchanged. ([#6494](https://github.com/valkey-io/valkey-glide/pull/6494))
* CI: Publish the Python `valkey-glide` and `valkey-glide-sync` packages to PyPI via Trusted Publishing (OIDC) with PEP 740 attestations, replacing API-token uploads ([#6478](https://github.com/valkey-io/valkey-glide/pull/6478))
* Node: Replace socket IPC with direct NAPI layer ([#5325](https://github.com/valkey-io/valkey-glide/pull/5325))
* feat(python-sync): add zero-copy buffers to mget ([#6367](https://github.com/valkey-io/valkey-glide/pull/6367))
* Python: Add configurable `lib_name` and `client_info_tag` to client configuration (async and sync). ([#6378](https://github.com/valkey-io/valkey-glide/issues/6378))
* Python: Add OpenTelemetry span creation for script invocations (`EVALSHA`) so `invoke_script` calls appear in traces with DB semantic convention attributes ([#6350](https://github.com/valkey-io/valkey-glide/pull/6350))
* Core, Java: add mTLS client certificates with automatic reloading ([#6386](https://github.com/valkey-io/valkey-glide/pull/6386))
* Go: add mTLS client certificates with automatic reloading ([#6384](https://github.com/valkey-io/valkey-glide/pull/6384))
* Node: add mTLS client certificate/key support with automatic certificate reloading ([#6383](https://github.com/valkey-io/valkey-glide/pull/6383))
* Python: add automatic mTLS client certificate/key reload ([#6596](https://github.com/valkey-io/valkey-glide/pull/6596))
