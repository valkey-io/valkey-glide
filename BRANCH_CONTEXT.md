# JNI Branch Context

## Branch Focus
- Replace UDS transport with JNI for the Java client while protecting the existing API and protobuf workflows.
- Current priority sequence: finalize response conversion/routing fidelity, then stabilize observability and error mapping.

## Status Snapshot (2025-09-15)
- Response pipeline now normalizes large DirectByteBuffer replies (`*` → `Object[]`, `%` → `LinkedHashMap`) and derives text vs binary from `expectUtf8`.
- Script path fully JNI-backed: Java `Script` objects store/load bytes via `ScriptResolver` and call `GlideNativeBridge.executeScriptAsync` → `Client::invoke_script` with routing preserved.
- `buildAll` skips FFI tests by default; use `./gradlew :client:buildAllWithFfi` if needed.

## Outstanding Issues
1. TYPE command still returns `GlideString`; ensure String output when API expects text.
2. Binary/string conversion edge cases—check context-aware decoding in CommandManager and unit `GlideClientTest.objectEncoding_binary_returns_success`.
3. AZ affinity routing needs verification in cluster scenarios.
4. OpenTelemetry exporter must write spans to `/tmp/spans.json` during tests.
5. Error mapping: surface precise Java exceptions (e.g., `ClosingException`).

## Script Redesign Highlights
- Script registration is local-only (SHA-1 cached in `scripts_container`); invocation retries EVALSHA with SCRIPT LOAD fallback using identical routing.
- Non-invocation commands (SHOW/EXISTS/FLUSH/KILL) still go through standard request path.
- Risks: decoding mismatch for `binaryOutput`, refcount leaks—tests cover binary payloads and `Script.close()` idempotency.

## Testing & Debug Tips
- Fast unit cycle: `cd java && ./gradlew :client:test -x spotbugsMain -x spotbugsTest`.
- Integration TLS default: `./gradlew :integTest:test -Dtls=true [-Dcluster-endpoints=host:port]`.
- Focused suites: `RUST_LOG=debug ./gradlew :integTest:test --tests "glide.cluster.CommandTests.*script*"`.
- Watch JaCoCo reports in `client/build/reports/jacoco` and `integTest/build/reports/jacoco`.

## Next Steps Checklist
- Align unit binary response path with integration behavior.
- Re-run cluster scan, AZ-affinity, and OpenTelemetry suites after fixes.
- Document any JNI migration deltas in `java/JNI_MIGRATION_STATUS.md` when progress is made.
