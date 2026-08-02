# Changelog

## Pending 2.6

### Fixes

* Node: Fix flaky NodeDiscoveryMode tests by polling CLIENT LIST ([#6690](https://github.com/valkey-io/valkey-glide/pull/6690))
* Core/FFI: Percent-decode userinfo in `create_client_from_uri` so credentials containing URI-reserved characters (`@`, `:`, `/`, `?`, `#`, `%`, `+`, space, non-ASCII) authenticate correctly ([#6659](https://github.com/valkey-io/valkey-glide/issues/6659))
* Core/All: Buffer pending cluster requests during reconnect instead of failing immediately. When a circular MOVED redirect triggers a reconnect, requests arriving during the recovery window are now queued and retried transparently once reconnection completes. A new `recovery_requests_queue_size` option (default: 1000) controls the queue depth. ([#6640](https://github.com/valkey-io/valkey-glide/pull/6640))
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
