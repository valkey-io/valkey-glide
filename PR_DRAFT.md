## JNI: Script path refactor, PubSub queue enablement, and review findings

### Summary
- Removed legacy script plumbing on the Java hot path and reimplemented script flow over JNI using `glide-core`'s `Client::invoke_script` with routing preserved.
- Restored minimal `ScriptResolver` native methods (`storeScript`, `dropScript`), and made `Script` store/drop through core by hash.
- Added a dedicated JNI entrypoint for script execution with explicit/auto routing and correct UTF-8 vs binary decoding.
- Implemented pull-based PubSub queue in `BaseClient` and restored handler/queue unit tests.
- Fixed `GlideCoreClient.createClient(...)` wrapper to forward `readFrom`, `clientAz`, `lazyConnect`, `clientName`, and PubSub subscriptions to JNI (removed hard-coded defaults). Focused unit + integration tests passed.
- PubSub end-to-end over JNI: added native push forwarder on eager create, added RESP3-only guard for subscriptions, and isolated user callback exceptions. PubSub integration suite (non-TLS) now passes via dedicated Gradle task.
- Centralized script routing mapping in `CommandManager` to keep parity with command routing and reduce duplication. Re-ran script integration tests for standalone and cluster – all passed.

### Scope (this PR)
- Java client JNI script path parity with UDS: storage, invocation, routing.
- PubSub: Java-side message queue and tests, plus JNI push callback plumbing review (see Fix Plan below).
- Hot-path cleanups (objectEncoding specialization). No TLS/observability scope expansion beyond existing code.

### Not in scope (follow-ups tracked below)
- TLS fixes, OpenTelemetry/observability changes beyond current usage.
- Full DirectByteBuffer lifecycle management improvements.

### UDS → JNI impact
- Scripts: UDS `ScriptInvocation` proto no longer used in Java; we now call core directly via JNI and let `glide-core` handle `EVALSHA → SCRIPT LOAD → EVALSHA` fallback.
- Commands/Batches: Still serialized via protobuf and executed through JNI; routing logic is preserved.
- PubSub: Delivery is via native push → Java callback or queue; see Fix Plan to complete end-to-end wiring.

### Changes (files of interest)
- `java/client/src/main/java/glide/api/models/Script.java`: store/drop via `ScriptResolver`; keep API unchanged.
- `java/client/src/main/java/glide/ffi/resolvers/ScriptResolver.java`: natives + `NativeUtils.loadGlideLib()`.
- `java/src/lib.rs`:
  - Add `Java_glide_ffi_resolvers_ScriptResolver_{storeScript,dropScript}`.
  - Add `Java_glide_internal_GlideNativeBridge_executeScriptAsync` with explicit or auto routing; complete through `complete_callback` with `binary_mode = !expect_utf8`.
  - Keep `execute{Command,BinaryCommand,Batch}Async` entry points for non-script commands.
- `java/client/src/main/java/glide/internal/GlideCoreClient.java`: expose `executeScriptAsync`, push callback receiver `onNativePush` (registry hookup pending; see Fix Plan).
- `java/client/src/main/java/glide/internal/GlideCoreClient.java`: createClient(...) now forwards config values (no stub defaults) and passes subscription arrays to JNI.
- `java/client/src/main/java/glide/api/BaseClient.java`: implement PubSub pull APIs using `messageHandler.getQueue()`.
- `java/client/src/main/java/glide/api/BaseClient.java`: add RESP3-only guard when subscriptions configured; wrap user PubSub callback with try/catch in `__enqueuePubSubMessage`.
- `java/src/lib.rs`: spawn push forwarder in eager `createClient` when subscriptions are present; deliver via `onNativePush`.
- Tests restored:
  - `java/client/src/test/java/glide/connectors/handlers/MessageHandlerTests.java`
  - `java/client/src/test/java/glide/connectors/handlers/PubSubMessageQueueTests.java`

### Code Review Findings (and Fix Plan)
1) PubSub: client registry wiring (functional) — DONE
   - `BaseClient.createClient(...)` now calls `GlideCoreClient.registerClient(handle, this)` after construction; `GlideCoreClient.unregisterClient(handle)` is invoked on close. One early `registerClient(handle, null)` remains during `buildCommandManager`; optional cleanup to remove duplicate/no-op registration.

2) PubSub: subscriptions threaded to core (functional) — DONE
   - `ConnectionManager.connectToValkey(...)` builds `subExact/subPattern/subSharded` from `BaseClientConfiguration` and passes them to `GlideNativeBridge.createClient`.
   - JNI maps them to `ValkeyClientConfig.pubsub_subscriptions` and into `ConnectionRequest.pubsub_subscriptions`.
   - Eager path now also spawns the push forwarder to drain the unbounded channel and call `onNativePush`.

3) PubSub protocol guard and callback safety — DONE
   - RESP3-only guard enforced when subscriptions are configured.
   - User callback exceptions are caught and do not break push delivery.

3) Script routing mapping (maintainability)
   - Current script JNI maps routing via ad-hoc `routeType/routeParam` ints; command path uses protobuf `Routes` consistently.
   - Fix Plan (Java): add a tiny shared helper to map the existing `RouteInfo` to the triad used by script calls, so routing stays uniform. Optionally pass a serialized `Routes` for scripts too to minimize divergence.

4) DirectByteBuffer ownership (memory)
   - In `create_direct_byte_buffer`, Rust `Vec` is `mem::forget`’d after creating `DirectByteBuffer`; JVM does not automatically free Rust allocations.
   - Fix Plan: introduce a small registry (handle → Vec) with a Java Cleaner calling back into JNI to free, or copy to `byte[]` for simplicity when acceptable.

5) Logging in hot paths (perf)
   - Several `log::debug!` in tight loops/async paths. Reduce or gate by level.

6) Duplicate command execution flows (maintainability)
   - `executeCommandAsync` and `executeBinaryCommandAsync` are largely similar.
   - Fix Plan: unify under a single helper parameterized by `expect_utf8`.

7) JNI JString handling (safety)
   - Prefer not to use `JString::from_raw` on parameters we don’t own; use `JString::from(obj)` or pass `jni::sys::jstring` and fetch with `get_string`.

### Proposed Next Steps (Checklist)
- [x] Wire PubSub registration from `BaseClient` and unregister on close.
- [x] Thread subscription config Java → JNI → Rust (`pubsub_subscriptions`).
- [x] Spawn push forwarder in JNI `createClient` to deliver pushes.
- [x] Run non-TLS PubSub integration tests to validate end-to-end delivery.
- [ ] Centralize script routing arg mapping from existing `RouteInfo` for scripts.
- [ ] Trim debug logging in JNI hot paths.
- [ ] Unify `executeCommandAsync` and `executeBinaryCommandAsync` in Rust.
- [ ] Add DirectByteBuffer registry + Cleaner or switch to heap `byte[]` (correctness-first), then iterate toward zero-copy safely.
- [ ] Add tests for ByAddress/slot routing parity in scripts.
- [ ] Update documentation (`JNI_MIGRATION_STATUS.md`) with PubSub changes.

### Validation Plan (non-TLS for now)
- Unit (ran):
  - `./gradlew :client:test --tests "glide.connectors.handlers.*" -x spotbugsMain -x spotbugsTest` — PASS
- Integration (ran):
  - `glide.ConnectionTests.basic_client` (RESP3/RESP2) — PASS
  - `glide.standalone.StandaloneClientTests.register_client_name_and_version` — PASS
  - PubSub suite: `./gradlew :integTest:pubsubTest -Dtls=false -x spotbugsMain -x spotbugsTest` — PASS
  - Scripts: `./gradlew :integTest:test -Dtls=false --tests "glide.standalone.CommandTests.script*" --tests "glide.cluster.CommandTests.script*" -x spotbugsMain -x spotbugsTest` — PASS
- Docs: update `java/JNI_MIGRATION_STATUS.md` after PubSub verification (and note the dedicated pubsubTest task).

### Performance & Memory Notes
- Preserve zero-copy only when we can properly free native buffers; otherwise prefer heap byte[] to avoid leaks.
- Async callback workers and runtime threads are bounded via `GLIDE_TOKIO_WORKER_THREADS` and `GLIDE_CALLBACK_WORKER_THREADS` env vars; document for ops.

### Risks
- PubSub wiring touches connection creation and callback paths; verify no deadlocks/races when closing clients.
- Routing parity: ensure script routing matches command routing for edge cases (ALL_PRIMARIES, slot key/id, by-address).

### Documentation updates
- `java/ScriptRedesignPlan.md`: marked script items [DONE].
- `java/JNI_MIGRATION_STATUS.md`: reflect script path over JNI and PubSub wiring after fixes.

### Files touched (already in this branch)
- `java/src/lib.rs`, `java/src/jni_client.rs`
- `java/client/src/main/java/glide/internal/GlideCoreClient.java`
- `java/client/src/main/java/glide/api/BaseClient.java`
- `java/client/src/main/java/glide/ffi/resolvers/ScriptResolver.java`
- `java/client/src/main/java/glide/api/models/Script.java`
- Tests under `java/client/src/test/java/glide/connectors/handlers/`

### Reviewer notes
- Focus review on PubSub wiring (registration and subscriptions) and script routing parity.
- Confirm that the Java public API remains unchanged.


