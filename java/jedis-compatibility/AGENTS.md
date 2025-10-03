# AGENTS: Jedis Compatibility Layer Context for Agentic Tools

This file provides AI agents and developers with the minimum but sufficient context to work productively with the Valkey GLIDE Jedis Compatibility Layer. It covers build commands, testing, contribution requirements, and essential guardrails specific to the Jedis-compatible API implementation.

## Repository Overview

This is the Jedis Compatibility Layer for Valkey GLIDE, providing a drop-in replacement API that is compatible with the Jedis library. This allows existing Jedis applications to migrate to GLIDE with minimal or no code changes.

**Primary Language:** Java (JDK 11+)
**Build System:** Gradle (sub-module)
**Architecture:** Jedis-compatible API wrapper around GLIDE core client

**Key Components:**
- `src/main/java/redis/clients/jedis/` - Jedis-compatible API classes
- `src/test/java/` - Unit tests for compatibility layer
- `build.gradle` - Gradle build configuration with shadow JAR
- `compatibility-layer-migration-guide.md` - Migration documentation

## Architecture Quick Facts

**Core Implementation:** Java wrapper providing Jedis-compatible API over GLIDE core
**Client Types:** Jedis (standalone), JedisCluster (cluster), UnifiedJedis (unified interface)
**API Style:** Synchronous, Jedis-compatible method signatures
**Dependencies:** GLIDE client module, Apache Commons Pool 2

**Supported Platforms:** Same as GLIDE Java client
**Classifiers Available:** `osx-aarch_64`, `osx-x86_64`, `linux-aarch_64`, `linux-x86_64`, `linux_musl-aarch_64`, `linux_musl-x86_64`

**Package:** `io.valkey:valkey-glide-jedis-compatibility` on Maven Central
**Import Path:** `redis.clients.jedis.*` (Jedis-compatible)

## Build and Test Rules (Agents)

### Preferred (Gradle Tasks)
```bash
# Build commands
./gradlew :jedis-compatibility:compileJava        # Compile compatibility layer
./gradlew :jedis-compatibility:jar                # Build standard JAR
./gradlew :jedis-compatibility:shadowJar          # Build fat JAR with dependencies
./gradlew :jedis-compatibility:build              # Full build including tests

# Testing
./gradlew :jedis-compatibility:test               # Run unit tests
./gradlew :jedis-compatibility:check              # Run tests and quality checks

# Publishing
./gradlew :jedis-compatibility:publishToMavenLocal    # Publish to local Maven repository

# Documentation
./gradlew :jedis-compatibility:javadoc            # Generate Javadocs
./gradlew :jedis-compatibility:sourcesJar         # Build sources JAR
./gradlew :jedis-compatibility:javadocJar         # Build Javadoc JAR

# Dependencies
./gradlew :jedis-compatibility:dependencies       # Show dependency tree
```

### Raw Equivalents
```bash
# Manual compilation (not recommended)
javac -cp "client.jar:commons-pool2.jar:..." src/main/java/redis/clients/jedis/*.java

# Manual test execution
java -cp "..." org.junit.runner.JUnitCore TestClass

# Manual JAR creation
jar cf valkey-glide-jedis-compatibility.jar -C build/classes .
```

### Test Execution Options
```bash
# Run specific test
./gradlew :jedis-compatibility:test --tests 'JedisTest.testSetAndGet'

# Run with verbose output
./gradlew :jedis-compatibility:test --info

# Run tests with native library path
./gradlew :jedis-compatibility:test -Djava.library.path=../target/release
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
- `build/` - Gradle build artifacts
- `build/classes/` - Compiled Java classes
- `build/libs/` - Generated JAR files
- `build/docs/` - Generated Javadocs
- `build/tmp/` - Temporary build files
- Native libraries copied from client module

### Jedis Compatibility-Specific Rules
- **API Compatibility:** Maintain strict compatibility with Jedis method signatures
- **Package Structure:** Use `redis.clients.jedis.*` package names for compatibility
- **Drop-in Replacement:** Existing Jedis code should work without modification
- **Configuration Mapping:** Map Jedis configurations to GLIDE configurations transparently
- **Connection Pooling:** Implement Jedis-compatible pooling using Apache Commons Pool 2
- **Error Handling:** Translate GLIDE exceptions to Jedis-compatible exceptions
- **Module System:** Disabled for maximum compatibility (`modularity.inferModulePath = false`)

### Dependency Management
- **Client Dependency:** Must depend on GLIDE client module
- **Shadow JAR:** Relocates protobuf to avoid conflicts (`glide.com.google.protobuf`)
- **Native Libraries:** Automatically copies from client module build
- **Commons Pool 2:** Part of public API for connection pooling

## Project Structure (Essential)

```
jedis-compatibility/
├── src/
│   ├── main/java/redis/clients/jedis/    # Jedis-compatible API classes
│   │   ├── Jedis.java                    # Main client class
│   │   ├── JedisCluster.java             # Cluster client class
│   │   ├── UnifiedJedis.java             # Unified interface
│   │   ├── JedisPool.java                # Connection pool
│   │   └── config/                       # Configuration classes
│   └── test/java/                        # Unit tests
├── build.gradle                          # Gradle build configuration
├── README.md                             # Usage documentation
└── compatibility-layer-migration-guide.md    # Migration guide
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: `./gradlew :jedis-compatibility:build` succeeds
- [ ] Tests pass: `./gradlew :jedis-compatibility:test` succeeds
- [ ] Spotless formatting: `./gradlew :spotlessCheck` passes (inherited)
- [ ] Javadocs generate: `./gradlew :jedis-compatibility:javadoc` succeeds
- [ ] Shadow JAR builds: `./gradlew :jedis-compatibility:shadowJar` succeeds
- [ ] No build artifacts committed (check `.gitignore`)
- [ ] DCO signoff present: `git log --format="%B" -n 1 | grep "Signed-off-by"`
- [ ] Conventional commit format used
- [ ] Jedis API compatibility maintained
- [ ] Migration guide updated for new features
- [ ] Native library dependencies resolved

## Quick Facts for Reasoners

**Package:** `io.valkey:valkey-glide-jedis-compatibility` on Maven Central
**API Style:** Synchronous, Jedis-compatible method signatures
**Client Types:** Jedis (standalone), JedisCluster (cluster), UnifiedJedis (unified)
**Key Features:** Drop-in Jedis replacement, connection pooling, configuration mapping
**Dependencies:** GLIDE client module, Apache Commons Pool 2, protobuf (relocated)
**Migration:** Zero or minimal code changes from existing Jedis applications
**Platforms:** Same as GLIDE Java client (Linux, macOS with platform classifiers)

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Migration Guide:** [compatibility-layer-migration-guide.md](./compatibility-layer-migration-guide.md)
- **Main Java Client:** [../README.md](../README.md)
- **Development Setup:** [../DEVELOPER.md](../DEVELOPER.md)
- **Examples:** [../examples/java/](../../examples/java/)
- **API Documentation:** Generated Javadocs in build output
- **GLIDE Client AGENTS:** [../AGENTS.md](../AGENTS.md)
- **Root Project:** [../../README.md](../../README.md)
