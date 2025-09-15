# Repository Guidelines

This guide orients contributors to the repository’s layout, build/test flow, style rules, and PR expectations. Primary sources of truth are the code and tests; also consult `CLAUDE.md`. Keep `java/JNI_MIGRATION_STATUS.md` updated during the JNI migration.

## Project Structure & Module Organization
- `glide-core/`: Rust core driver (protocol, routing, scripts) with tests.
- `java/`: Java SDK with JNI bridge; key native code in `java/src/lib.rs`.
- `ffi/`: Rust FFI crate (C-compatible bridge for other wrappers).
- `logger_core/`: Rust logging utilities and tests.
- `utils/`: Dev/test helpers (TypeScript, `cluster_manager.py`).
- Branch focus: migrate Java from UDS to direct JNI.

## Build, Test, and Development Commands
- Java build/tests: `cd java && ./gradlew :client:buildAll && ./gradlew test`
- Java integration: `./gradlew :integTest:test [-Dtls=true] [-Dcluster-endpoints=host:port -Dstandalone-endpoints=host:port]`
- Target a case: `RUST_LOG=debug ./gradlew :integTest:test --tests "*scriptShow_test*"`
- Rust core: `cd glide-core && cargo build && cargo test`
- FFI crate: `cd ffi && cargo build --release && cargo test`
- Lint/format: `cargo clippy --all-features --all-targets -- -D warnings && cargo fmt --all && ./gradlew :spotlessCheck :spotlessApply && ./gradlew spotbugsMain`

## Coding Style & Naming Conventions
- Indentation: spaces (4); XML uses 2 (`.editorconfig`).
- Rust: `rustfmt`; `snake_case` files; `CamelCase` types; document public APIs.
- Java: Spotless; packages under `glide.api.*`; classes `CamelCase`, methods `camelCase`.
- JNI: stable signatures; use `CompletableFuture` for async, `GlideString` for binary-safe paths, `DirectByteBuffer` for payloads >16KB.

## Testing Guidelines
- Rust: unit in `src/`, integration in `tests/`; run `cargo test`.
- Java: unit in `java/client/src/test/java/...`, integration in `java/integTest/src/test/java/...`.
- Coverage: JaCoCo under `client/build/reports/jacoco` and `integTest/build/reports/jacoco`.
- Environments: prefer TLS locally (`-Dtls=true`); clusters via `-Dcluster-endpoints` or `utils/cluster_manager.py`.

## Commit & Pull Request Guidelines
- Commits: imperative subject ≤72 chars; optional scope (`jni:`, `java:`, `glide-core:`, `ffi:`); link issues (e.g., `#123`).
- PRs: include rationale, JNI vs UDS migration impact, testing notes, and docs updates (keep `java/JNI_MIGRATION_STATUS.md` current).
- Gates: Rust clippy/fmt, Java Spotless/SpotBugs, unit + integration tests green for changed areas.

## Security & Tooling
- Never commit secrets; prefer env vars and test certs.
- Java dev requires `protoc v29.1` (`protoc --version`).
- Validate TLS paths; prefer TLS in local tests.

