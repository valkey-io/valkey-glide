# Repository Guidelines

## Project Structure & Module Organization
- `glide-core/`: Rust driver for protocol handling, routing, and script execution; unit and integration tests live alongside source.
- `java/`: Java SDK with the JNI bridge; native bindings in `java/src/lib.rs`, Java sources under `client/src/main/java`, tests in `client/src/test/java` and `integTest/src/test/java`.
- `ffi/`: Rust FFI shim that exposes C-compatible bindings for other language wrappers.
- Supporting crates live in `logger_core/` (logging utilities) and `utils/` (TypeScript helpers, `cluster_manager.py`). Keep `java/JNI_MIGRATION_STATUS.md` current during UDS→JNI work.

## Build, Test, and Development Commands
- `cd glide-core && cargo build && cargo test`: Compile and run Rust core checks.
- `cd ffi && cargo build --release && cargo test`: Verify the FFI surface in release mode.
- `cd java && ./gradlew :client:buildAll && ./gradlew test`: Build the Java client and execute unit tests.
- `./gradlew :integTest:test -Dtls=true`: Execute integration tests against a TLS-enabled cluster; add `-Dcluster-endpoints=host:port` as needed.

## Coding Style & Naming Conventions
- Rust: 4-space indent, `rustfmt`, snake_case modules, CamelCase types, document public APIs.
- Java: Spotless formatting, packages under `glide.api.*`, classes CamelCase, methods camelCase, use `GlideString` and `DirectByteBuffer` for binary-safe JNI calls.
- General: Keep XML 2-space indented per `.editorconfig`.

## Testing Guidelines
- Run `cargo test` or `./gradlew test` before submitting changes touching the respective stack.
- Integration coverage tracked via JaCoCo reports in `client/build/reports/jacoco` and `integTest/build/reports/jacoco`.
- Prefer TLS paths locally and coordinate cluster endpoints through `utils/cluster_manager.py`.

## Commit & Pull Request Guidelines
- Commit subjects: imperative, ≤72 chars, optional scopes like `jni:` or `glide-core:`, link issues (e.g., `#123`).
- PRs should explain UDS→JNI impact, list validation steps, and note updates to docs such as `java/JNI_MIGRATION_STATUS.md`.
- Ensure Rust clippy/fmt, Java Spotless/SpotBugs, and relevant unit/integration suites pass.

## Security & Tooling Notes
- Do not commit secrets; rely on environment variables and test certificates.
- Java builds require `protoc v29.1` (`protoc --version`).
- Validate TLS asset paths and prefer secure defaults in local testing.
