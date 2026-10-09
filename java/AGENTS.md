# AGENTS: Java Client Context for Agentic Tools

This file provides AI agents with the minimum but sufficient context to work productively with the Valkey GLIDE Java client. It covers build commands, testing, contribution requirements, and essential guardrails specific to the Java implementation.

## Repository Overview

This is the Java client binding for Valkey GLIDE, providing both standalone and cluster client implementations. The Java wrapper communicates with the Rust core via JNI using protobuf protocol.

**Primary Language:** Java (JDK 11 to build; artifacts target Java 8 bytecode, `options.release = 8` in `build.gradle`)
**Build System:** Gradle
**Architecture:** Java wrapper around Rust FFI core with JNI bindings

**Key Components:**

- `client/` - Main Java client library and unit tests
- `integTest/` - Integration tests and E2E testing
- `jedis-compat-shared/` - Shared `redis.clients.jedis.*` types used by both Jedis compatibility artifacts
- `jedis-compatibility/` - Jedis 5.x–shaped compatible API layer for drop-in replacement
- `jedis-4-compatibility/` - Jedis 4.x–shaped compatible API layer
- `benchmarks/` - Performance benchmarking tool (Gradle project `:benchmarks`)
- `e2e/` - Standalone Docker-based E2E scenarios (circuit breaker, DNS failover, Lambda); not part of the Gradle build or CI
- `src/` - Rust JNI library (`glide-rs`, built by `Cargo.toml` in this directory)

## Architecture Quick Facts

**Core Implementation:** Java wrapper around glide-core Rust library via JNI
**Client Types:** GlideClient (standalone), GlideClusterClient (cluster)
**API Style:** Async-first with CompletableFuture return types
**Protocol:** Protobuf communication with Rust core

**Supported Platforms and Classifiers:** see [README.md › System Requirements](./README.md#system-requirements) and [README.md › Classifiers](./README.md#classifiers). Alpine Linux/MUSL is not supported at runtime even though `linux_musl-*` classifiers are published.

## Build and Test Rules (Agents)

### Environment

- `protoc` 29.x on `PATH` (`protobuf-java` is pinned to 4.29.6; a newer protoc generates incompatible code and the build's version check does not reject it)
- `cargo` on `PATH`; `JAVA_HOME` pointing at a JDK 11+
- After changing `PATH`/`JAVA_HOME`, run `./gradlew --stop`: daemons cache the environment (see [DEVELOPER.md](./DEVELOPER.md#troubleshooting))
- **Every `./gradlew` invocation in this directory runs `pkill -f valkey-server` and `pkill -f redis-server` when the build finishes** (`gradle.buildFinished` hook in `integTest/build.gradle`), even for unrelated tasks such as `./gradlew tasks`. On a machine with servers you do not own, set `GLIDE_DOCKER_SERVERS=true` to skip the hook.
- `:integTest:test` and `:integTest:pubsubTest` stop every `cluster*` server started by `utils/cluster_manager.py`, delete `../utils/clusters/`, and start fresh standalone/cluster/TLS/AZ servers before running

### Preferred (Gradle Tasks)

```bash
# Build all components (protobuf codegen + cargo build --release + build, which runs unit tests, checkstyle, spotbugs)
./gradlew :client:buildAll

# Testing
./gradlew test                    # Run all tests in every subproject, including integTest (long; starts servers)
./gradlew :client:test            # Unit tests only (~1300 tests, ~1.5 min)
./gradlew :integTest:test         # Integration/E2E tests (excludes PubSubTests and glide.modules.*)
./gradlew :integTest:pubsubTest   # PubSub integration tests
./gradlew :integTest:scopeTest    # glide.scope.* integration tests

# Linting and Code Quality
./gradlew :spotlessCheck          # Check code formatting
./gradlew :spotlessApply          # Apply code formatting
./gradlew checkstyleMain          # Checkstyle (config/checkstyle/checkstyle.xml); also runs as part of `check`
./gradlew spotbugsMain            # SpotBugs; reports in client/build/reports/spotbugs/main/, never fails the build (ignoreFailures = true)

# Code Coverage
# JaCoCo reports are produced automatically after each Test task (finalizedBy) under <project>/build/reports/jacoco/.
# `./gradlew jacocoTestReport` invoked on its own fails (missing task dependency on :client:copyNativeLib).

# Publishing (integTest depends on the client being in ~/.m2)
./gradlew publishToMavenLocal

# Benchmarks
./gradlew :benchmarks:run --args="--help"
```

### Raw Equivalents

```bash
# What :client:buildAll runs, from java/
./gradlew protobuf       # protoc -> client/src/main/java/glide/models/protobuf/ (requires protoc >= 29)
cargo build --release    # -> target/release/libglide_rs.{dylib,so}; Linux CI uses cargo zigbuild instead
```

### Test Execution Options

```bash
# Run with TLS
./gradlew :integTest:test -Dtls=true

# Run against existing endpoints (only skips starting the non-TLS servers; the stop/clearDirs chain and the TLS servers still run)
./gradlew :integTest:test -Dcluster-endpoints=localhost:7000 -Dstandalone-endpoints=localhost:6379

# Run specific test (method name pattern; `*.foo` matches a method named exactly foo)
./gradlew :integTest:test --tests '*.set_and_get_without_options' --rerun
./gradlew :client:test --tests '*.latencyHistory*' --rerun

# Run specific test class
./gradlew :client:test --tests 'BatchTests' --rerun

# Server modules test (needs an existing cluster with the modules loaded; does not start servers)
./gradlew :integTest:modulesTest -Dcluster-endpoints=localhost:7000 -Dtls=true
```

## Contribution Requirements

### Developer Certificate of Origin (DCO) Signoff REQUIRED

All commits must include a `Signed-off-by` line:

```bash
# Add signoff to new commits
git commit -s -m "feat(java): add new command implementation"

# Configure automatic signoff
git config --global format.signOff true

# Add signoff to existing commit
git commit --amend --signoff --no-edit

# Add signoff to multiple commits
git rebase -i HEAD~n --signoff
```

### Conventional Commits

Use conventional commit format:

```
<type>(<scope>): <description>

[optional body]
```

**Example:** `feat(java): implement CLUSTER SCAN command with routing options`

### Code Quality Requirements

**Spotless (Code Formatting):**

```bash
./gradlew :spotlessCheck    # Must pass before commit
./gradlew :spotlessApply    # Fix formatting issues
```

**SpotBugs (Static Analysis):**

```bash
./gradlew spotbugsMain      # Reports in client/build/reports/spotbugs/main/; findings do not fail the build, read the report
```

**Rust Components:**

```bash
# From java/ directory
rustup component add clippy rustfmt
cargo clippy --all-features --all-targets -- -D warnings
cargo fmt --manifest-path ./Cargo.toml --all
```

## Guardrails & Policies

### Generated Outputs (Never Commit)

- `build/`, `client/build/`, `integTest/build/`, `benchmarks/build/`, `jedis-*/build/` - Gradle build artifacts (JaCoCo and SpotBugs reports live under `<project>/build/reports/`)
- `target/` - Rust build artifacts
- `.gradle/` - Gradle cache
- `client/src/main/java/glide/models/protobuf/` - Generated protobuf classes (gitignored via the `protobuf` pattern)
- `gradle.properties` - gitignored; holds Maven Central credentials when present

### Java-Specific Rules

- **JDK 11 to build, Java 8 at runtime:** Gradle toolchain is JDK 11; compilation targets Java 8 bytecode (`options.release = 8`), so no Java 9+ APIs in `client/`
- **Classifier Required:** Must specify platform classifier in dependencies
- **Async Pattern:** All client methods return CompletableFuture
- **Resource Management:** Use try-with-resources for client instances
- **Protobuf Updates:** Run `./gradlew protobuf` after proto changes (also runs automatically before `compileJava`)

### Command Implementation Guidelines

- Extend BaseClient for both standalone and cluster implementations
- Implement interfaces from `glide.api.commands` package
- Add unit tests in GlideClientTest/GlideClusterClientTest
- Add integration tests in appropriate test files
- Update BaseBatch.java for batch API support
- Include comprehensive Javadocs with Valkey version info and links

## Project Structure (Essential)

```
java/
├── client/                      # Main Java client library
│   ├── src/main/java/glide/     # Client implementation (models/protobuf/ is generated)
│   └── src/test/java/glide/     # Unit tests
├── integTest/                   # Integration and E2E tests
│   └── src/test/java/glide/     # Integration test suites (standalone/, cluster/, modules/, pool/, scope/)
├── jedis-compat-shared/         # Shared types for Jedis 4.x/5.x compatibility layers
├── jedis-compatibility/         # Jedis 5.x–shaped compatibility layer
├── jedis-4-compatibility/       # Jedis 4.x–shaped compatibility layer
├── benchmarks/                  # Performance benchmarking tool
├── e2e/                         # Docker-based manual E2E scenarios (not in the Gradle build)
├── src/                         # Rust JNI library (glide-rs)
├── config/checkstyle/           # Checkstyle rules
├── Cargo.toml                   # Rust crate for src/
├── build.gradle                 # Root Gradle build (spotless, checkstyle, jacoco)
└── settings.gradle              # Gradle settings (subproject list)
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: `./gradlew :client:buildAll` succeeds
- [ ] Unit tests pass: `./gradlew :client:test` succeeds
- [ ] Spotless formatting: `./gradlew :spotlessCheck` passes
- [ ] SpotBugs: `./gradlew spotbugsMain` report in `client/build/reports/spotbugs/main/` has no new findings (the task never fails on its own)
- [ ] Integration tests pass: `./gradlew :integTest:test` succeeds (requires a local `valkey-server`; see Environment above for what it stops and deletes)
- [ ] No build artifacts committed (check `.gitignore`)
- [ ] DCO signoff present: `git log --format="%B" -n 1 | grep "Signed-off-by"`
- [ ] Conventional commit format used
- [ ] Javadocs updated for new public APIs
- [ ] Platform classifier specified in dependencies

## Quick Facts for Reasoners

**Package:** `io.valkey:valkey-glide` on Maven Central
**API Style:** Async with CompletableFuture, try-with-resources pattern
**Client Types:** GlideClient (standalone), GlideClusterClient (cluster)
**Key Features:** Jedis compatibility layers (4.x and 5.x), benchmarking tool, JNI-based Rust integration
**Testing:** Unit tests (`:client:test`), integration tests (`:integTest:test`, `pubsubTest`, `scopeTest`, `modulesTest`)
**Platforms:** see [README.md › System Requirements](./README.md#system-requirements)
**Dependencies:** JDK 11 to build (Java 8+ at runtime), platform-specific native libraries

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Supported Engine Versions:** [root README table](../README.md#supported-engine-versions)
- **Development Setup:** [DEVELOPER.md](./DEVELOPER.md)
- **Examples:** [../examples/java/](../examples/java/)
- **API Documentation:** Generated Javadocs in build output
- **Integration Tests:** [integTest/src/test/java/glide/](./integTest/src/test/java/glide/)
- **Benchmarks:** [benchmarks/](./benchmarks/) directory
- **Command Interfaces:** [client/src/main/java/glide/api/commands/](./client/src/main/java/glide/api/commands/)
- **Jedis Compatibility Layer:** [jedis-compatibility/AGENTS.md](./jedis-compatibility/AGENTS.md)
