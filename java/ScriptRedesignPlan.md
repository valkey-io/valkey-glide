## Script Redesign Plan (JNI) — Spec, Plan, Steps, Tasks

### Scope and Goals
- Preserve the public Java API (interfaces/classes and method signatures) under `glide.api.*`.
- Remove prior UDS-specific script plumbing; reimplement minimal JNI-backed flow.
- Do not change `glide-core` logic; only reuse available capabilities.
- Keep the hot path simple and allocation-light; avoid extra hashing/routing logic in Java.

### Constraints and Invariants
- Public API: keep `glide/api/models/Script`, `ScriptingAndFunctions*Commands`, `GlideClient`, `GlideClusterClient` intact.
- Native/Rust: leverage existing `glide_core::scripts_container` and `Client::invoke_script`.
- Routing: follow existing rules — keys must share hash slot in cluster; when keys absent, route as per existing strategy (primary/random as applicable).
- Observability: keep current OpenTelemetry behavior; no added spans in the hot path for script.

### Core Design
- Construction and Drop (local only):
  - `Script(code, binaryOutput)` computes/stores code in Rust-global `scripts_container`, returns SHA1.
  - `Script.close()` decrements and removes from `scripts_container` when refcount reaches 0.
  - No network on construct/close; purely local ops.
- Invocation (execute):
  - Java submits a native call that invokes by `sha1` with keys/args and optional route.
  - Rust `Client::invoke_script` executes `EVALSHA`; on `NOSCRIPT` uses container bytes to `SCRIPT LOAD` and retries.
  - Routing inferred from keys when route not provided (EVALSHA-shaped command), or use explicit route if provided.
- Non-invocation SCRIPT commands (SHOW/EXISTS/FLUSH/KILL):
  - Submitted as regular commands using existing `RequestType` via the same JNI command path.

### glide-core Capabilities Used (no changes required)
- `glide_core::scripts_container::{add_script, get_script, remove_script}`
- `glide_core::client::Client::invoke_script(hash, keys, args, routing)`
- Protobuf RequestType already includes: ScriptShow, ScriptExists, ScriptFlush, ScriptKill

### Operation-by-Operation Behavior
- Load (on Script construction):
  - Store code bytes in `scripts_container`; return SHA1.
  - Java retains SHA1 and `binaryOutput` flag.
- Show (SCRIPT SHOW):
  - `submitNewCommand(RequestType.ScriptShow, [sha1])` → returns source.
- Exists (SCRIPT EXISTS):
  - `submitNewCommand(RequestType.ScriptExists, [sha1...])` → returns `Boolean[]`.
- Flush (SCRIPT FLUSH):
  - `submitNewCommand(RequestType.ScriptFlush, [mode?])` with routing as required (e.g., primaries in cluster).
- Kill (SCRIPT KILL):
  - `submitNewCommand(RequestType.ScriptKill, [])` with route as needed.
- Execute (invoke):
  - Prefer explicit `Route` if provided, else auto-route by keys using an EVALSHA-shaped probe to derive routing.
  - Call native `executeScriptAsync(handle, callbackId, sha1, keys[], args[], route?, expectUtf8)`; `expectUtf8 = !binaryOutput`.

### Java Changes
- `glide/api/models/Script` (internal only):
  - On construct: call `ScriptResolver.storeScript(byte[] code)` → `String sha1`.
  - On `close()`: call `ScriptResolver.dropScript(String sha1)` once.
  - Keep `binaryOutput` accessor; no internal logic beyond the above.
- `glide/ffi/resolvers/ScriptResolver` (restore minimal):
  - `static { NativeUtils.loadGlideLib(); }`
  - `public static native String storeScript(byte[] code);`
  - `public static native void dropScript(String sha1);`
- `glide/managers/CommandManager`:
  - Update `submitScript(Script, List<GlideString> keys, List<GlideString> args, ...)` and routed variant to call JNI `GlideNativeBridge.executeScriptAsync(...)` path rather than building `ScriptInvocation` protobuf.
  - Pass `expectUtf8 = script.getBinaryOutput() == null || !script.getBinaryOutput()` to align decoding.
  - Keep SHOW/EXISTS/FLUSH/KILL via existing `submitNewCommand(RequestType, ...)` helpers.

### JNI/Rust Bridge Changes (java/src/lib.rs)
- Add externs for the resolver:
  - `Java_glide_ffi_resolvers_ScriptResolver_storeScript(byte[] code) -> jstring` → `scripts_container::add_script(code)`.
  - `Java_glide_ffi_resolvers_ScriptResolver_dropScript(String sha1)` → `scripts_container::remove_script(sha1)`.
- Ensure `Java_glide_internal_GlideNativeBridge_executeScriptAsync(...)`:
  - Accepts `expect_utf8` (boolean) or derive it from Java call site.
  - Uses explicit `Route` when provided; otherwise builds EVALSHA-shaped command to compute routing (already present).
  - Calls `client.invoke_script(...)` and completes callback using `binary_mode = !expect_utf8`.

### Testing Contract Alignment
- After first `invokeScript`, `scriptExists([sha1])` returns true.
- After `scriptFlush`, `invokeScript` auto-loads from container and succeeds.
- `scriptKill` semantics follow server (may error if no script running).
- Cluster routing respects keys/route rules; `ALL_PRIMARIES` for FLUSH where required.
- No API surface changes; all existing unit/integ tests should remain applicable once stubs replaced.

### Risks and Mitigations
- Decoding mode mismatch: wire `binaryOutput` → `expectUtf8` explicitly; add tests for binary payloads.
- Memory leak risk with leaked vectors: reuse existing `GlideValueResolver.createLeakedBytesVec` patterns only when necessary (large arg sets). Invocation path uses slices, not leaked vectors.
- Refcount correctness: guard `Script.close()` idempotently.

### Implementation Steps
1) Java API wiring
   - Reinstate `glide/ffi/resolvers/ScriptResolver` with natives.
   - Update `glide/api/models/Script` constructor/close to call resolver.
2) JNI functions
   - Implement `storeScript`/`dropScript` externs mapping to `scripts_container`.
3) Execute path
   - Update `CommandManager.submitScript(...)` to call native `executeScriptAsync` and pass `expectUtf8`.
   - Ensure route propagation (explicit vs auto by keys).
   - Adjust `executeScriptAsync` to use provided `expectUtf8` when completing response.
4) Keep SHOW/EXISTS/FLUSH/KILL on existing `submitNewCommand` path (no changes to Rust).
5) Tests
   - Re-enable/adjust script unit/integration tests (standalone/cluster, binary/text, flush/kill/exists/show).

### Task Breakdown (internal)
- Add Java resolver and wire `Script`:
  - Create `java/client/src/main/java/glide/ffi/resolvers/ScriptResolver.java` with natives.
  - Edit `java/client/src/main/java/glide/api/models/Script.java` to use resolver on construct/close.
- Add JNI externs in `java/src/lib.rs`:
  - `storeScript` → `scripts_container::add_script`
  - `dropScript` → `scripts_container::remove_script`
- Update invocation flow:
  - Edit `java/client/src/main/java/glide/managers/CommandManager.java` `submitScript(...)` methods to call `GlideNativeBridge.executeScriptAsync` with keys/args/route and `expectUtf8`.
  - Edit `java/src/lib.rs` `Java_glide_internal_GlideNativeBridge_executeScriptAsync` to accept/use `expect_utf8` when completing callback.
- Validate non-invocation SCRIPT commands via existing `submitNewCommand`.
- Run test suites:
  - `cd java && ./gradlew :client:buildAll && ./gradlew test`
  - Integration as needed: `./gradlew :integTest:test -Dtls=true` with endpoints

### Done Criteria
- All script-related unit and integration tests pass in standalone and cluster mode.
- No regressions in non-script commands.
- No glide-core modifications required; only Java/JNI changes.


