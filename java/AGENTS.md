# AGENTS: Java Client Context for Agentic Tools

This file provides AI agents with the minimum but sufficient context to work productively with the Valkey GLIDE Java client. It covers build commands, testing, contribution requirements, and essential guardrails specific to the Java implementation.

## Repository Overview

This is the Java client binding for Valkey GLIDE, providing both standalone and cluster client implementations. The Java wrapper communicates with the Rust core via JNI using protobuf protocol.

**Primary Language:** Java (JDK 11+)
**Build System:** Gradle
**Architecture:** Java wrapper around Rust FFI core with JNI bindings

**Key Components:**
- `client/` - Main Java client library and unit tests
- `integTest/` - Integration tests and E2E testing
- `jedis-compatibility/` - Jedis-compatible API layer for drop-in replacement
- `benchmark/` - Performance benchmarking tool
- `src/` - Rust FFI integration code

## Architecture Quick Facts

**Core Implementation:** Java wrapper around glide-core Rust library via JNI
**Client Types:** GlideClient (standalone), GlideClusterClient (cluster)
**API Style:** Async-first with CompletableFuture return types
**Protocol:** Protobuf communication with Rust core

**Supported Platforms:**
- Linux: Ubuntu 20+, Amazon Linux 2/2023 (x86_64, aarch64)
- macOS: 13.7+ (x86_64), 14.7+ (aarch64)
- **Note:** Alpine Linux/MUSL not supported due to native Java component incompatibility

**Classifiers Available:**
- `osx-aarch_64`
- `osx-x86_64`
- `linux-aarch_64`
- `linux-x86_64`
- `linux_musl-aarch_64`
- `linux_musl-x86_64`
- `windows-x86_64`

## Build and Test Rules (Agents)

### Preferred (Gradle Tasks)
```bash
# Build all components
./gradlew :client:buildAll
./gradlew :client:buildAllRelease

# Testing
./gradlew test                    # Run all tests
./gradlew :client:test           # Unit tests only
./gradlew :client:testFfi        # FFI tests (Java-Rust interface)
./gradlew :integTest:test        # Integration/E2E tests
./gradlew :integTest:pubsubTest  # PubSub integration tests

# Linting and Code Quality
./gradlew :spotlessCheck         # Check code formatting
./gradlew :spotlessApply         # Apply code formatting
./gradlew spotbugsMain           # Run SpotBugs static analysis

# Code Coverage
./gradlew jacocoTestReport       # Generate JaCoCo coverage reports

# Publishing
./gradlew publishToMavenLocal    # Publish to local Maven repository

# Benchmarks
./gradlew run --args="--help"    # Show benchmark options
```

### Raw Equivalents
```bash
# Manual build steps (not recommended)
javac -cp "..." src/main/java/**/*.java
jar cf valkey-glide.jar -C build/classes .

# Manual test execution
java -cp "..." org.junit.runner.JUnitCore TestClass

# Manual protobuf generation
./gradlew protobuf
```

### Test Execution Options
```bash
# Run with TLS
./gradlew :integTest:test -Dtls=true

# Run against existing endpoints
./gradlew :integTest:test -Dcluster-endpoints=localhost:7000 -Dstandalone-endpoints=localhost:6379

# Run specific test
./gradlew :integTest:test --tests '*.functionLoad_and_functionList' --rerun

# Run specific test class
./gradlew :client:test --tests 'BatchTests' --rerun

# Server modules test
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
./gradlew spotbugsMain      # Generate reports in build/reports/spotbugs/
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
- `build/` - Gradle build artifacts
- `client/build/` - Client build outputs
- `integTest/build/` - Integration test artifacts
- `benchmark/build/` - Benchmark build outputs
- `target/` - Rust build artifacts
- `.gradle/` - Gradle cache
- `generated/` - Generated protobuf files
- JaCoCo reports in `build/reports/jacoco/`
- SpotBugs reports in `build/reports/spotbugs/`

### Java-Specific Rules
- **JDK 11+ Required:** Minimum Java version for compilation and runtime
- **Classifier Required:** Must specify platform classifier in dependencies
- **Module System:** Include `requires glide.api;` in module-info.java
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
├── jedis-compatibility/         # Jedis-compatible API layer
├── benchmark/                   # Performance benchmarking tool
├── src/                        # Rust FFI integration
├── build.gradle                # Main Gradle build configuration
├── gradle.properties           # Gradle properties
└── settings.gradle             # Gradle settings
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: `./gradlew :client:buildAll` succeeds
- [ ] All tests pass: `./gradlew test` succeeds
- [ ] Spotless formatting: `./gradlew :spotlessCheck` passes
- [ ] SpotBugs analysis: `./gradlew spotbugsMain` generates clean reports
- [ ] FFI tests pass: `./gradlew :client:testFfi` succeeds
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
**Testing:** Unit tests, FFI tests, integration tests, PubSub tests
**Platforms:** Linux (Ubuntu, AL2/AL2023), macOS (Intel/Apple Silicon)
**Dependencies:** JDK 11+, platform-specific native libraries

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Development Setup:** [DEVELOPER.md](./DEVELOPER.md)
- **Examples:** [../examples/java/](../examples/java/)
- **API Documentation:** Generated Javadocs in build output
- **Integration Tests:** [integTest/src/test/java/glide/](./integTest/src/test/java/glide/)
- **Benchmarks:** [benchmark/](./benchmark/) directory
- **Command Interfaces:** [client/src/main/java/glide/api/commands/](./client/src/main/java/glide/api/commands/)
