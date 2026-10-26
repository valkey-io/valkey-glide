# AGENTS: Java Client Context for Agentic Tools

This file provides AI agents with the minimum but sufficient context to work productively with the Valkey GLIDE Java client — build commands, testing, and Java-specific guardrails. It builds on [../AGENTS.md](../AGENTS.md) (repo-wide conventions, DCO, commit format, test servers) — read that first.

## Repository Overview

This is the Java client binding for Valkey GLIDE, providing both standalone and cluster client implementations. The Java wrapper communicates with the Rust core via JNI using protobuf protocol.

**Primary Language:** Java (builds with JDK 11+, runs on Java 8+)
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
- `src/` - Rust JNI library (crate `glide-rs`, built by `Cargo.toml` in this directory)

## Architecture Quick Facts

**Core Implementation:** Java wrapper around glide-core Rust library via JNI
**Client Types:** GlideClient (standalone), GlideClusterClient (cluster)
**API Style:** Async-first with CompletableFuture return types
**Protocol:** Protobuf communication with Rust core

**Supported Platforms** (what runs where in CI differs — don't read the classifier list as "all tested every PR"):
- Linux glibc (x86_64, aarch64) and macOS aarch64 — tested on **every PR/push**
- Linux musl/Alpine (x86_64 only; aarch64-musl is build-only) — **nightly / full-matrix only** (container job gated behind `full-matrix || schedule`)
- Windows (x86_64) — supported and CI-capable, but **excluded from the default run** (filtered out unless the `windows-only` dispatch is used; integration tests run via WSL)
- macOS x86_64 — CI tests skipped (CD only)

**Classifiers Available:**
- `osx-aarch_64`
- `osx-x86_64`
- `linux-aarch_64`
- `linux-x86_64`
- `linux_musl-aarch_64`
- `linux_musl-x86_64`
- `windows-x86_64`

## Build and Test Rules (Agents)

<!-- These commands are intentionally mirrored in java/DEVELOPER.md (human guide). Keep both in sync when the build/test/lint commands change. -->

### Environment

- **Every `./gradlew` invocation in this directory kills servers machine-wide when the build finishes.** `integTest/build.gradle`'s `gradle.buildFinished` hook runs `pkill -f valkey-server` and `pkill -f redis-server` (via WSL on Windows) — even for unrelated tasks like `./gradlew tasks` — unless `GLIDE_DOCKER_SERVERS=true` is set. On a shared machine or in parallel sessions this kills test servers you did not start. Also, `:integTest:test`/`pubsubTest` stop every `cluster*` folder under `utils/clusters/` and start fresh ones before running.
- `protoc` 29.x on `PATH` — `protobuf-java` is pinned to 4.29.6, and a newer protoc (e.g. 36.x) generates incompatible code; the build's own version check does not reject it, so you'll get a `com.google.protobuf.GeneratedFile`-not-found error instead of a clear version mismatch.
- After changing `PATH`/`JAVA_HOME`, run `./gradlew --stop` — Gradle daemons cache the environment they started with.

### Preferred (Gradle Tasks)
```bash
# Build all components (buildAll = protobuf + buildRust (release) + :client:build only —
# it does NOT build :integTest, :jedis-*, or :benchmarks)
./gradlew :client:buildAll

# Testing
./gradlew :client:test            # Unit tests (~1300 tests)
./gradlew :integTest:test         # Integration tests (excludes PubSubTests AND modules.* — run those via pubsubTest / modulesTest)
./gradlew :integTest:pubsubTest   # PubSub integration tests
./gradlew :integTest:scopeTest    # glide.scope.* integration tests

# Linting and Code Quality
./gradlew :spotlessCheck         # Check code formatting
./gradlew :spotlessApply         # Apply code formatting
./gradlew spotbugsMain           # Run SpotBugs static analysis

# Code Coverage — JaCoCo reports are produced automatically after each Test task
# (finalizedBy); running `./gradlew jacocoTestReport` on its own fails (missing
# dependency on :client:copyNativeLib). Run a Test task first, or let it run.

# Publishing
./gradlew publishToMavenLocal    # Publish to local Maven repository (integTest depends on this)

# Benchmarks
./gradlew :benchmarks:run --args="--help"    # Show benchmark options
```

### Raw Equivalents
```bash
# There is no working hand-rolled build: the client needs protobuf codegen
# (compileJava.dependsOn('protobuf')) and the JNI native lib on java.library.path,
# and tests are JUnit 5 (useJUnitPlatform). Always drive it through Gradle.

# Regenerate protobuf (first-class Gradle task, not a manual step)
./gradlew :client:protobuf
```

### Test Servers

Integration tests (`:integTest:test`, `:integTest:pubsubTest`, `make java-test`) **auto-start** standalone + cluster servers via `utils/cluster_manager.py` and stop them afterward — you don't start anything manually for a normal run. **But note the machine-wide `pkill` build-finish hook under "Environment" above** — it stops *all* valkey/redis-server processes, not just ones this build started. The `-Dcluster-endpoints=`/`-Dstandalone-endpoints=` options below are only for pointing at a server you started yourself; see the root [../AGENTS.md](../AGENTS.md) "Test Servers" section. A `valkey-server`/`redis-server` on `PATH` is required either way.

### Test Execution Options
```bash
# Run with TLS
./gradlew :integTest:test -Dtls=true

# Run against existing endpoints
./gradlew :integTest:test -Dcluster-endpoints=localhost:7000 -Dstandalone-endpoints=localhost:6379

# Run specific test (method name pattern; `*.foo` matches a method named exactly foo)
./gradlew :integTest:test --tests '*.set_and_get_without_options' --rerun
./gradlew :client:test --tests '*.latencyHistory*' --rerun

# Run specific test class
./gradlew :client:test --tests 'BatchTests' --rerun

# Server modules test
./gradlew :integTest:modulesTest -Dcluster-endpoints=localhost:7000 -Dtls=true
```

## Contribution Requirements

DCO signoff and Conventional Commits are required repo-wide — see [../AGENTS.md](../AGENTS.md#contribution-requirements) for the exact commands. Use the `java` scope, e.g. `feat(java): implement CLUSTER SCAN command with routing options`.

### Code Quality Requirements

**Spotless (Code Formatting):**
```bash
./gradlew :spotlessCheck    # Must pass before commit
./gradlew :spotlessApply    # Fix formatting issues
```

**SpotBugs (Static Analysis):**
```bash
./gradlew spotbugsMain      # reports in client/build/reports/spotbugs/main/ (ignoreFailures=true — does NOT fail the build)
```

**Checkstyle:** runs on all subprojects (`toolVersion 10.21.4`, `config/checkstyle/checkstyle.xml`) and gates `build` via `check`. `make java-lint` runs `:spotlessApply` **and** `checkstyleMain checkstyleTest`:
```bash
./gradlew checkstyleMain checkstyleTest
```

**Rust Components** (the embedded JNI glue — CI lints this from `java/` via the shared Rust gate):
```bash
# From java/ — runs the full repo-wide Rust gate (fmt --check, clippy ×2, cargo deny, cargo doc).
# See ../AGENTS.md for the exact command list.
cargo fmt --all -- --check
cargo clippy --all-features --all-targets -- -D warnings
```

## Guardrails & Policies

### Generated Outputs (Never Commit)
- `build/` - Gradle build artifacts
- `client/build/` - Client build outputs
- `integTest/build/` - Integration test artifacts
- `benchmarks/build/` - Benchmark build outputs
- `target/` - Rust build artifacts
- `.gradle/` - Gradle cache
- Generated protobuf sources in `client/src/main/java/glide/models/protobuf/`
- JaCoCo reports in `client/build/reports/jacoco/`
- SpotBugs reports in `client/build/reports/spotbugs/main/`

### Java-Specific Rules
- **Compile toolchain pinned to JDK 11, runtime supports Java 8+:** the Gradle Java toolchain is set to exactly 11 with `options.release = 8`, so the compile JDK is auto-provisioned at 11 even if you launch Gradle on 21. What needs "11+" is the Gradle/Spotless *launcher* JVM. CI compiles on JDK 11 and runs tests on Java 8 (`-PtestJava8=true`) as an always-run matrix row.
- **Classifier Required:** Must specify platform classifier in dependencies
- **Maven Version:** Building/consuming via Maven needs Maven 3.9+ (uses `os-maven-plugin`); an old 3.0.x `mvn` fails on it. Gradle is the primary build tool — this applies when you drive the client through Maven.
- **Async Pattern:** All client methods return CompletableFuture
- **Resource Management:** Use try-with-resources for client instances
- **Protobuf Updates:** Run `./gradlew protobuf` after proto changes

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
│   ├── src/main/java/glide/     # Client implementation
│   └── src/test/java/glide/     # Unit tests
├── integTest/                   # Integration and E2E tests
│   └── src/test/java/glide/     # Integration test suites
├── jedis-compat-shared/         # Shared types for Jedis 4.x/5.x compatibility layers
├── jedis-compatibility/         # Jedis 5.x–shaped compatibility layer
├── jedis-4-compatibility/       # Jedis 4.x–shaped compatibility layer
├── benchmarks/                  # Performance benchmarking tool (:benchmarks)
├── src/                        # Rust FFI integration
├── build.gradle                # Main Gradle build configuration
└── settings.gradle             # Gradle settings
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: `./gradlew :client:buildAll` succeeds
- [ ] All tests pass: `./gradlew :client:test` (unit) and `./gradlew :integTest:test` (integration) succeed — `./gradlew test` runs the `test` task in every subproject (needs servers running)
- [ ] Spotless formatting: `./gradlew :spotlessCheck` passes
- [ ] SpotBugs analysis: `./gradlew spotbugsMain` generates clean reports
- [ ] Integration tests pass: `./gradlew :integTest:test` succeeds
- [ ] No build artifacts committed (check `.gitignore`)
- [ ] DCO signoff present: `git log --format="%B" -n 1 | grep "Signed-off-by"`
- [ ] Conventional commit format used
- [ ] Javadocs updated for new public APIs
- [ ] Platform classifier specified in dependencies

## Quick Facts for Reasoners

**Package:** `io.valkey:valkey-glide` on Maven Central
**API Style:** Async with CompletableFuture, try-with-resources pattern
**Client Types:** GlideClient (standalone), GlideClusterClient (cluster)
**Key Features:** Jedis compatibility layer, comprehensive benchmarking, JNI-based Rust integration
**Testing:** Unit tests (`:client:test`), integration tests (`:integTest:test`, `pubsubTest`, `scopeTest`, `modulesTest`)
**Platforms:** Linux (Ubuntu, AL2/AL2023), macOS (Intel/Apple Silicon)
**Dependencies:** JDK 11+ to build (Java 8+ runtime), platform-specific native libraries

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Development Setup:** [DEVELOPER.md](./DEVELOPER.md)
- **Jedis compatibility layer:** [jedis-compatibility/AGENTS.md](./jedis-compatibility/AGENTS.md) (build, test and guardrails for the `jedis-compatibility`, `jedis-4-compatibility` and `jedis-compat-shared` modules described above)
- **Examples:** [../examples/java/](../examples/java/)
- **API Documentation:** Generated Javadocs in build output
- **Integration Tests:** [integTest/src/test/java/glide/](./integTest/src/test/java/glide/)
- **Benchmarks:** [benchmarks/](./benchmarks/) directory
- **Command Interfaces:** [client/src/main/java/glide/api/commands/](./client/src/main/java/glide/api/commands/)
