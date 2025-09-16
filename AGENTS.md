# Repository Guidelines

## Project Structure & Module Organization
- `glide-core/`: Rust driver handling protocol routing and script execution; unit and integration tests live beside sources.
- `java/`: Java SDK and JNI bridge. Native bindings in `java/src/lib.rs`; Java sources under `client/src/main/java`; tests in `client/src/test/java` and integration suites in `integTest/src/test/java`.
- `ffi/`: Rust FFI shim exposing C-compatible bindings for other language wrappers.
- Supporting crates live in `logger_core/` (logging utilities) and `utils/` (TypeScript helpers, `cluster_manager.py`). Keep `java/JNI_MIGRATION_STATUS.md` updated when touching JNI migration.

## Build, Test, and Development Commands
- `cd glide-core && cargo build && cargo test`: Compile and validate the Rust core.
- `cd ffi && cargo build --release && cargo test`: Check the FFI surface in release mode.
- `cd java && ./gradlew :client:buildAll && ./gradlew test`: Build the Java client and execute unit tests.
- `./gradlew :integTest:test -Dtls=true`: Run TLS-enabled integration tests, optionally add `-Dcluster-endpoints=host:port`.

## Coding Style & Naming Conventions
- Rust: 4-space indent, run `cargo fmt && cargo clippy`, snake_case modules, CamelCase types, document public APIs.
- Java: Spotless formatting via `./gradlew spotlessApply`, packages under `glide.api.*`, CamelCase classes, camelCase methods, and use `GlideString`/`DirectByteBuffer` for JNI-safe binaries.
- XML and config files follow `.editorconfig` (2-space indent where specified).

## Testing Guidelines
- Prefer TLS scenarios when possible; coordinate endpoints with `utils/cluster_manager.py`.
- Keep unit and integration tests near their sources; name tests for behavior under test.
- Review JaCoCo reports in `client/build/reports/jacoco` and `integTest/build/reports/jacoco` to track coverage.

## Commit & Pull Request Guidelines
- Commit subjects: imperative, ≤72 chars; include optional scopes (e.g., `jni:`) and reference issues (`#123`).
- Before submitting, ensure Rust fmt/clippy and Java Spotless/SpotBugs along with relevant test suites pass.
- PRs should summarize UDS→JNI impact, list validation steps, and mention documentation updates such as `java/JNI_MIGRATION_STATUS.md`.

## Security & Tooling Notes
- Never commit secrets; rely on environment variables and bundled test certificates when needed.
- Java builds require `protoc v29.1` (`protoc --version`). Verify TLS asset paths and prefer secure defaults in local testing.
