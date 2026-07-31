# AGENTS: Unified Context for Agentic Tools

This file provides AI agents with the minimum but sufficient context to work productively in the Valkey GLIDE mono-repository. It covers build commands, contribution requirements, and essential guardrails for maintaining code quality across multiple language bindings.

## Repository Overview

This is the Valkey GLIDE mono-repository containing a Rust core (`glide-core`) and FFI layer used to build first-class Valkey/Redis clients with multi-language bindings. The repository implements the General Language Independent Driver for the Enterprise (GLIDE) for Valkey and Redis OSS.

**Primary Languages Present:** Rust, Java, Python, Node.js/TypeScript, Go

**Key Components:**

- `glide-core/` - Core Rust implementation with async client logic
- `ffi/` - Foreign Function Interface layer for language interoperability
- `java/` - Java client bindings with Gradle build system
- `python/` - Python async/sync client bindings
- `node/` - Node.js/TypeScript client bindings with npm
- `go/` - Go client bindings
- `logger_core/` - Shared logging infrastructure
- `utils/` - Shared utilities and cluster management tools
- `benchmarks/` - Performance benchmarks across languages
- `examples/` - Usage examples for each language binding
- `docs/` - Documentation and MkDocs configuration

## Architecture Quick Facts

**Core Implementation:** Rust (`glide-core`) with FFI exposure to language adapters
**Design Constraints:** Async-first APIs, cluster-aware routing, batching support, cross-AZ affinity
**Key Features:** Multi-slot command handling, PubSub auto-reconnection, cluster scan, OpenTelemetry integration

### RESP2/RESP3 Response Normalization

Valkey supports two wire protocols (RESP2 and RESP3) that may return structurally different responses for the same commands. For example, RESP2 returns flat arrays where RESP3 returns maps, and RESP2 returns bulk strings where RESP3 returns typed doubles.

The Rust core normalizes these differences in `glide-core/src/client/value_conversion.rs` so that language bindings receive a consistent data structure regardless of protocol version; language bindings should *not* need to handle RESP2/RESP3 differences themselves. When adding a new command whose RESP2 and RESP3 responses differ, add or reuse an `ExpectedReturnType` variant and implement the conversion logic in `convert_to_expected_type`.

**Supported Engine Versions:**

| Engine Type | 6.2 | 7.0 | 7.1 | 7.2 | 8.0 | 8.1 |
|-------------|-----|-----|-----|-----|-----|-----|
| Valkey      | -   | -   | -   | ✓   | ✓   | ✓   |
| Redis       | ✓   | ✓   | ✓   | ✓   | -   | -   |

## Build and Test Rules (Agents)

### Preferred (Make Targets)

```bash
# Build all language bindings
make all

# Individual language builds
make java          # Build Java client
make python        # Build Python async + sync clients (release mode)
make node          # Build Node.js client (release mode)
make go            # Build Go client

# Testing
make java-test     # Run Java integration tests
make python-test   # Run Python tests
make node-test     # Run Node.js tests
make go-test       # Run Go tests

# Linting
make java-lint     # Run Java spotlessApply
make python-lint   # Run Python linters via dev.py
make node-lint     # Run Node.js linters
make go-lint       # Run Go linters

# Utilities
make clean         # Remove .build/ directory
make help          # List available targets
```

### Raw Equivalents Per Stack

**Rust (glide-core):**

```bash
cd glide-core
cargo build --release
cargo test
cargo bench
cargo clippy
cargo fmt
```

**Java:**

```bash
cd java

./gradlew :client:cleanRust
./gradlew :client:clean
./gradlew :client:buildRust
./gradlew :client:buildAll
./gradlew :spotlessApply

# Unit tests
./gradlew :client:test                             # Run all unit tests
./gradlew :client:test --tests 'BatchTests'        # Run unit tests from a class
./gradlew :client:test --tests '*.latencyHistory'  # Run unit tests with a pattern

# Integration tests
./gradlew :integTest:test                               # Run all integration tests
./gradlew :integTest:test --tests 'SharedCommandTests'  # Run integration tests from a class
./gradlew :integTest:test --tests '*.latencyHistory'    # Run integration tests with a pattern
```

**Python:**

```bash
cd python

# Build
python3 dev.py build --mode release               # Build both clients in release mode
python3 dev.py build --client async --mode debug  # Build async client only in debug node (faster)

# Lint (isort, black, flake8, mypy)
python3 dev.py lint          # Fix formatting
python3 dev.py lint --check  # Check only

# Integration tests
python3 dev.py test                          # Run all tests
python3 dev.py test --args -k "test_memory"  # Run all tests matching a pattern

# Clean (Rust and Python artifacts)
python3 dev.py clean                 # Clean both client artifacts
python3 dev.py clean --client async  # Clean shared and async client artifacts

```

**Node.js/TypeScript:**

```bash
cd node

# Install and build
npm ci
npm run build:release  # Build Rust and TypeScript (slow)
npm run build:ts       # Build TypeScript only (fast)

# Lint
npm run lint:fix

# Integration tests
npm test                                     # Run all tests
npm test -- --testNamePattern='memoryStats'  # Run tests matching a pattern
npm test -- --testPathPattern='GlideClient'  # Run tests from a specific file
```

**Go:**

```bash
cd go
make build
make test
make lint
go build ./...
go test ./...
```

**Benchmarks:**

```bash
# Rust benchmarks
cd glide-core && cargo bench

# Cross-language benchmarks
cd benchmarks && ./install_and_test.sh
```

**Test Results:** Stored in language-specific directories (`target/`, `build/`, `node_modules/`, etc.)

## Contribution Requirements

### Developer Certificate of Origin (DCO) Signoff REQUIRED

All commits must include a `Signed-off-by` line:

```bash
# Add signoff to new commits
git commit -s -m "feat: add new feature"

# Configure automatic signoff
git config --global format.signOff true

# Add signoff to existing commit
git commit --amend --signoff --no-edit

# Add signoff to multiple commits
git rebase -i HEAD~n --signoff
```

**Required format:** `Signed-off-by: Your Name <your.email@example.com>`

### Conventional Commits

Use conventional commit format for all commit messages:

```text
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

**Common types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

**Example:** `feat(java): add cluster scan support for Java client`

## Guardrails & Policies

### Generated Outputs (Never Commit)

- `target/` - Rust build artifacts
- `node_modules/` - Node.js dependencies
- `.build/` - Make build cache
- `debug/` - Debug builds
- `generated/` - Generated protobuf files
- `benchmarks/results/` - Benchmark output
- `python/.env*` - Python virtual environments
- `*.class` - Java compiled files
- Language-specific build directories per `.gitignore`

### Cross-Language Changes

- Follow semantic versioning for breaking changes
- Test changes across affected language bindings

### Security & Code Quality

- Never commit secrets, credentials, or API keys
- Follow SECURITY.md for vulnerability reporting
- Run lint/format targets before committing
- Maintain compatibility with supported engine versions
- Do not modify vendored or third-party code

## Project Structure (Essential)

```text
valkey-glide/
├── glide-core/          # Core Rust implementation
├── ffi/                 # Foreign Function Interface layer
├── java/                # Java client bindings (Gradle)
├── python/              # Python async/sync bindings
├── node/                # Node.js/TypeScript bindings (npm)
├── go/                  # Go client bindings
├── logger_core/         # Shared logging infrastructure
├── utils/               # Cluster management and utilities
├── benchmarks/          # Performance benchmarks
├── examples/            # Usage examples per language
├── docs/                # Documentation (MkDocs)
├── .github/workflows/   # CI/CD pipelines
└── Makefile            # Top-level build orchestration
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: `make all` succeeds
- [ ] Lint passes: `make *-lint` targets succeed
- [ ] Tests pass: `make *-test` targets succeed
- [ ] No generated outputs committed (check `.gitignore`)
- [ ] DCO signoff present: `git log --format="%B" -n 1 | grep "Signed-off-by"`
- [ ] Conventional commit format used
- [ ] Cross-language API consistency maintained
- [ ] Security scan passes (no secrets committed)

## Quick Facts for Reasoners

**Engines Supported:** Valkey 7.2, 8.0, 8.1, 9.0+ | Redis 6.2, 7.0, 7.1, 7.2
**Key Features:** AZ Affinity, PubSub auto-reconnection, sharded PubSub, cluster-aware multi-key commands, cluster scan, batching (pipeline/transaction), OpenTelemetry integration
**Architecture:** Rust core with FFI bindings, async-first design, cluster and standalone support
**Performance:** Optimized for high throughput and low latency with connection pooling

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Contributing:** [CONTRIBUTING.md](./CONTRIBUTING.md)
- **Security:** [SECURITY.md](https://github.com/valkey-io/.github/blob/main/SECURITY.md)
- **Documentation:** [docs/README.md](./docs/README.md)
- **Examples:** [examples/](./examples/)
- **Language-Specific Guides:**
  - [Java Developer Guide](./java/DEVELOPER.md)
  - [Python Developer Guide](./python/DEVELOPER.md)
  - [Node.js Developer Guide](./node/DEVELOPER.md)
  - [Go Developer Guide](./go/DEVELOPER.md)
