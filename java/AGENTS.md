# Repository Guidelines

## Project Structure & Module Organization
The Java wrapper lives beside the Rust core in this workspace. `client/` contains the publishable Java library plus unit tests, while `src/` hosts the JNI bridge and Rust crate defined by `Cargo.toml`. Integration scenarios sit under `integTest/`, relying on helper scripts in `../utils`. Benchmark harnesses are split between `benchmarks/` for Java-focused runs and `polyglot-benchmarks/` for cross-language comparisons. Generated artifacts land in `build/` (Gradle) and `target/` (Cargo); keep them out of source control.

## Build, Test, and Development Commands
Run `./gradlew :client:buildAll` from `java/` to regenerate protobuf stubs, compile Rust through `cargo`/`cargo-zigbuild`, and assemble the JAR. Execute `./gradlew :client:test` for unit tests and `./gradlew :integTest:test` once local Valkey instances are available (the Gradle task auto-starts clusters via `utils/cluster_manager.py`). Use `./gradlew :spotlessApply` and `./gradlew spotbugsMain` before reviews. Rust-side checks live at the workspace root: `cargo fmt --all` and `cargo clippy --all-features --all-targets`.

## Coding Style & Naming Conventions
Spotless enforces Google Java Format 1.22.0 with the Valkey GLIDE license header; Java code should use four-space indentation, `glide.*` package names, and UpperCamelCase types. Lombok is common for DTOs—mirror existing annotations. Rust modules must remain `snake_case` and formatted with `rustfmt`. Keep protobuf outputs generated under `client/src/main/java/glide/models/protobuf` untouched.

## Testing Guidelines
JUnit 5 backs both unit and integration suites; name tests `<ClassName>Test` and prefer `@Nested` classes for grouping. JaCoCo class-level coverage verification runs automatically after tests—inspect `client/build/reports/jacoco` or `integTest/build/reports/jacoco` when failures occur. Integration tests expect Valkey binaries on `PATH`; export `cluster-endpoints` or `standalone-endpoints` system properties to reuse shared environments, and toggle TLS with `-Dtls=true`.

## Commit & Pull Request Guidelines
Follow the repository’s narrative commit style: concise descriptions such as “Adjust pipeline timeout handling” and include issue references with `(#1234)` where applicable. Each PR should outline scope, testing evidence, and any configuration changes; attach logs or screenshots for tooling output when clarifying behavior. Verify Spotless, SpotBugs, Clippy, and all relevant Gradle tasks locally before requesting review, and mention any intentionally skipped checks.
