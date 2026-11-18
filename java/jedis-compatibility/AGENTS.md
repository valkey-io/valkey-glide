# AGENTS: Jedis Compatibility Layer Context for Agentic Tools

This file provides AI agents and developers with the minimum but sufficient context to work productively with the Valkey GLIDE Jedis Compatibility Layer. It covers build commands, testing, contribution requirements, and essential guardrails specific to the Jedis-compatible API implementation.

## Repository Overview

This is the Jedis Compatibility Layer for Valkey GLIDE, providing a drop-in replacement API that is compatible with the Jedis library. This allows existing Jedis applications to migrate to GLIDE with minimal or no code changes.

**Primary Language:** Java (JDK 11 to build; Java 8 bytecode, inherited from the root `build.gradle`)
**Build System:** Gradle (sub-module `:jedis-compatibility` of `java/`)
**Architecture:** Jedis-compatible API wrapper around GLIDE core client

**Key Components:**

- `src/main/java/redis/clients/jedis/` - Jedis 5.x–shaped entry points: `Jedis`, `JedisCluster`, `UnifiedJedis`, `JedisPool`, `JedisPooled`, plus `params/`, `bloom/`, `timeseries/`
- `../jedis-compat-shared/` - Everything shared with `jedis-4-compatibility`: configuration (`JedisClientConfig`, `DefaultJedisClientConfig`, `ConfigurationMapper`, `ClusterConfigurationMapper`), `exceptions/`, `commands/`, `args/`, `resps/`, `Protocol`. Most `redis.clients.jedis.*` types live here, not in this module
- `src/test/java/` - Unit tests (108 tests, no server needed)
- `../integTest/src/test/java/compatibility/jedis/` - Integration tests against real servers, run by `:integTest:test`
- `build.gradle` - Gradle build configuration (plain JAR with platform classifier; no shadow JAR)
- `compatibility-layer-migration-guide.md` - Migration documentation

## Architecture Quick Facts

**Core Implementation:** Java wrapper providing Jedis-compatible API over GLIDE core
**Client Types:** Jedis (standalone), JedisCluster (cluster), UnifiedJedis (unified interface), JedisPool / JedisPooled (pooled)
**API Style:** Synchronous, Jedis-compatible method signatures
**Dependencies:** `:jedis-compat-shared` (api), `:client` (implementation), Apache Commons Pool 2 (api), Apache Commons Lang 3

**Supported Platforms and Classifiers:** same as the GLIDE Java client, see [../README.md › Classifiers](../README.md#classifiers)

**Package:** `io.valkey:valkey-glide-jedis-compatibility` on Maven Central
**Import Path:** `redis.clients.jedis.*` (Jedis-compatible)

## Build and Test Rules (Agents)

### Preferred (Gradle Tasks)

Run from `java/`. Prerequisites and the `pkill valkey-server` build-finish hook are described in [../AGENTS.md › Environment](../AGENTS.md#environment); `compileJava` depends on `:client:jar`, so the client (and its Rust library) is built on demand.

```bash
# Build commands
./gradlew :jedis-compatibility:compileJava        # Compile compatibility layer
./gradlew :jedis-compatibility:jar                # JAR with platform classifier -> build/libs/jedis-compatibility-<classifier>.jar
./gradlew :jedis-compatibility:build              # Full build: jar, checkstyle (main + test), unit tests

# Testing
./gradlew :jedis-compatibility:test               # Unit tests (no server needed)
./gradlew :jedis-compatibility:check              # Run tests and quality checks
./gradlew :jedis-compatibility:checkstyleMain     # Checkstyle only

# Publishing
./gradlew :jedis-compatibility:publishToMavenLocal    # Publish to local Maven repository (version 255.255.255 unless GLIDE_RELEASE_VERSION is set)

# Documentation
./gradlew :jedis-compatibility:javadoc            # Generate Javadocs (from delombok output; failOnError = false)
./gradlew :jedis-compatibility:sourcesJar         # Build sources JAR
./gradlew :jedis-compatibility:javadocJar         # Build Javadoc JAR

# Dependencies
./gradlew :jedis-compatibility:dependencies       # Show dependency tree
```

### Raw Equivalents

None: this is a plain Java module with no code generation. The native library is not copied into this module; it ships inside the `:client` jar under `natives/<os>/<arch>/` (`copyNativeLib` in `client/build.gradle`) and `NativeUtils.loadGlideLib()` extracts it from there at runtime, so `./gradlew :client:buildAll` must have run once before these tests. The `-Djava.library.path` jvmArgs in this module's `build.gradle` are not used by the loader.

### Test Execution Options

```bash
# Run specific test method / class (unit tests)
./gradlew :jedis-compatibility:test --tests '*.ConfigurationTest.testBasicConfiguration'
./gradlew :jedis-compatibility:test --tests 'ConfigurationTest'

# Run with verbose output
./gradlew :jedis-compatibility:test --info

# Integration tests for this layer live in :integTest (starts servers; see ../AGENTS.md)
./gradlew :integTest:test --tests 'compatibility.jedis.*'
```

## Contribution Requirements

### Developer Certificate of Origin (DCO) Signoff REQUIRED

All commits must include a `Signed-off-by` line:

```bash
# Add signoff to new commits
git commit -s -m "feat(jedis-compatibility): add new Jedis-compatible method"

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

**Example:** `feat(jedis-compatibility): implement JedisPool connection pooling`

### Code Quality Requirements

**Spotless (Code Formatting):**

```bash
./gradlew :spotlessCheck    # Must pass before commit (inherited from root)
./gradlew :spotlessApply    # Fix formatting issues (inherited from root)
```

**Lombok Support:**

- Uses Lombok for boilerplate reduction
- Delombok task generates source for Javadocs
- Annotation processing configured for compile and test

## Guardrails & Policies

### Generated Outputs (Never Commit)

- `build/` - Gradle build artifacts (`build/classes/`, `build/libs/`, `build/docs/`, `build/tmp/`, `build/reports/`)

### Jedis Compatibility-Specific Rules

- **API Compatibility:** Maintain strict compatibility with Jedis 5.x method signatures (4.x shapes go in `../jedis-4-compatibility`)
- **Package Structure:** Use `redis.clients.jedis.*` package names for compatibility
- **Drop-in Replacement:** Existing Jedis code should work without modification
- **Shared First:** A type needed by both the 4.x and 5.x layers belongs in `../jedis-compat-shared`, not here
- **Configuration Mapping:** `ConfigurationMapper` / `ClusterConfigurationMapper` (in `jedis-compat-shared`) map Jedis configurations to GLIDE configurations
- **Connection Pooling:** `JedisPool`, `JedisPooled`, `JedisCluster` pool through Apache Commons Pool 2 (`AbstractGlideJedisFactory` in `jedis-compat-shared`)
- **Error Handling:** Translate GLIDE exceptions to the `redis.clients.jedis.exceptions.*` hierarchy in `jedis-compat-shared`
- **Module System:** Disabled for maximum compatibility (`modularity.inferModulePath = false`)

### Dependency Management

- **Client Dependency:** `implementation project(':client')`; `api project(':jedis-compat-shared')` and `api commons-pool2` are part of the public API
- **No Shadow JAR:** This module publishes a plain JAR. Protobuf relocation (`glide.com.google.protobuf`) happens in the client's shadow JAR, which is what `:client` resolves to
- **Native Libraries:** Not bundled here; loaded at runtime from the `:client` jar's `natives/<os>/<arch>/` resources by `NativeUtils.loadGlideLib()`. Run `./gradlew :client:buildAll` once so the library exists
- **Classifier:** `osdetector.classifier` (e.g. `osx-aarch_64`); set `JEDIS_NO_CLASSIFIER_BUILD=true` for a classifier-less JAR

## Project Structure (Essential)

```
jedis-compatibility/
├── src/
│   ├── main/java/redis/clients/jedis/    # Jedis 5.x–shaped API classes
│   │   ├── Jedis.java                    # Main client class
│   │   ├── JedisCluster.java             # Cluster client class
│   │   ├── UnifiedJedis.java             # Unified interface
│   │   ├── JedisPool.java                # Connection pool
│   │   ├── JedisPooled.java              # Pooled single-connection facade
│   │   ├── GlideJedisFactory.java        # Commons Pool 2 factory
│   │   ├── CommandObjects.java, ClusterConnectionProvider.java
│   │   ├── params/                       # Command parameter classes
│   │   ├── bloom/, timeseries/           # Module protocol constants
│   └── test/java/redis/clients/jedis/    # Unit tests
├── build.gradle                          # Gradle build configuration
├── README.md                             # Usage documentation
└── compatibility-layer-migration-guide.md    # Migration guide

Related, outside this directory:
../jedis-compat-shared/src/main/java/redis/clients/jedis/   # config, exceptions/, commands/, args/, resps/, Protocol, ...
../integTest/src/test/java/compatibility/jedis/            # Integration tests (JedisTest, JedisClusterTest, JedisPoolTest, ...)
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: `./gradlew :jedis-compatibility:build` succeeds
- [ ] Tests pass: `./gradlew :jedis-compatibility:test` succeeds
- [ ] Spotless formatting: `./gradlew :spotlessCheck` passes (inherited)
- [ ] Javadocs generate: `./gradlew :jedis-compatibility:javadoc` succeeds
- [ ] No build artifacts committed (check `.gitignore`)
- [ ] DCO signoff present: `git log --format="%B" -n 1 | grep "Signed-off-by"`
- [ ] Conventional commit format used
- [ ] Jedis API compatibility maintained
- [ ] Migration guide updated for new features
- [ ] Shared types added to `../jedis-compat-shared` (and `../jedis-4-compatibility` updated) when both layers need them

## Quick Facts for Reasoners

**Package:** `io.valkey:valkey-glide-jedis-compatibility` on Maven Central
**API Style:** Synchronous, Jedis-compatible method signatures
**Client Types:** Jedis (standalone), JedisCluster (cluster), UnifiedJedis (unified), JedisPool / JedisPooled
**Key Features:** Drop-in Jedis replacement, connection pooling, configuration mapping
**Dependencies:** `:jedis-compat-shared`, `:client` (shadow JAR with relocated protobuf and bundled native library), Apache Commons Pool 2
**Migration:** Zero or minimal code changes from existing Jedis applications
**Platforms:** Same as GLIDE Java client, see [../README.md › Classifiers](../README.md#classifiers)

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Migration Guide:** [compatibility-layer-migration-guide.md](./compatibility-layer-migration-guide.md)
- **Shared Types:** [../jedis-compat-shared/](../jedis-compat-shared/)
- **Jedis 4.x Layer:** [../jedis-4-compatibility/](../jedis-4-compatibility/)
- **Integration Tests:** [../integTest/src/test/java/compatibility/jedis/](../integTest/src/test/java/compatibility/jedis/)
- **Main Java Client:** [../README.md](../README.md)
- **Development Setup:** [../DEVELOPER.md](../DEVELOPER.md)
- **Examples:** [../../examples/java/](../../examples/java/)
- **API Documentation:** Generated Javadocs in build output
- **GLIDE Client AGENTS:** [../AGENTS.md](../AGENTS.md)
- **Root Project:** [../../README.md](../../README.md)
