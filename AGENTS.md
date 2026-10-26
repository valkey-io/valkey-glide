# AGENTS: Unified Context for Agentic Tools

This file provides AI agents with the minimum but sufficient context to work productively in the Valkey GLIDE mono-repository. It covers build commands, contribution requirements, and essential guardrails for maintaining code quality across multiple language bindings.

## Repository Overview

This is the Valkey GLIDE mono-repository containing a Rust core (`glide-core`) and FFI layer used to build first-class Valkey/Redis clients with multi-language bindings. The repository implements the General Language Independent Driver for the Enterprise (GLIDE) for Valkey and Redis OSS.

**Primary Languages Present:** Rust, Java, Python, Node.js/TypeScript, Go

**Clients in separate repositories** — each has its own `AGENTS.md`; load it from that repo when working on the client:

- C# - [valkey-io/valkey-glide-csharp](https://github.com/valkey-io/valkey-glide-csharp) ([AGENTS.md](https://github.com/valkey-io/valkey-glide-csharp/blob/main/AGENTS.md), [DEVELOPER.md](https://github.com/valkey-io/valkey-glide-csharp/blob/main/DEVELOPER.md))
- PHP - [valkey-io/valkey-glide-php](https://github.com/valkey-io/valkey-glide-php) ([AGENTS.md](https://github.com/valkey-io/valkey-glide-php/blob/main/AGENTS.md), [DEVELOPER.md](https://github.com/valkey-io/valkey-glide-php/blob/main/DEVELOPER.md))
- Ruby - [valkey-io/valkey-glide-ruby](https://github.com/valkey-io/valkey-glide-ruby) ([AGENTS.md](https://github.com/valkey-io/valkey-glide-ruby/blob/main/AGENTS.md), [DEVELOPER.md](https://github.com/valkey-io/valkey-glide-ruby/blob/main/DEVELOPER.md))

**Key Components:**

- `rust/` - Native Rust client (preview). See [rust/AGENTS.md](./rust/AGENTS.md).
- `glide-core/` - Core Rust implementation with async client logic. See [glide-core/AGENTS.md](./glide-core/AGENTS.md).
- `glide-core/redis-rs/` - Vendored fork of redis-rs 0.25.2 (GLIDE-owned, not third-party).
- `ffi/` - Foreign Function Interface layer (used by Go and both Python clients).
- `java/` - Java client bindings with Gradle build system (JNI). See [java/AGENTS.md](./java/AGENTS.md).
- `python/` - Python async + sync client bindings (CFFI into `ffi/`; PyO3 for async response parsing). See [python/AGENTS.md](./python/AGENTS.md).
- `node/` - Node.js/TypeScript client bindings via NAPI-RS. See [node/AGENTS.md](./node/AGENTS.md).
- `go/` - Go client bindings (CGO). See [go/AGENTS.md](./go/AGENTS.md).
- `glide-telemetry/` - OpenTelemetry integration crate.
- `glide-logger/` - Shared logging infrastructure.
- `glide-rdma/` - RDMA transfer layer over libfabric (not yet used by clients).
- `utils/` - Shared utilities and cluster management tools (`cluster_manager.py`).
- `benchmarks/` - Performance benchmarks across languages.
- `examples/` - Usage examples for each language binding.
- `docs/` - Internal/in-repo docs. The published docs live in [valkey-io/valkey-glide-docs](https://github.com/valkey-io/valkey-glide-docs).

Each binding has its own `AGENTS.md` — read it after this file for the binding you're changing. `CLAUDE.md` at the repo root holds additional hard constraints that apply alongside this file. The Rust crates (`rust/`, `glide-core/`, `glide-core/redis-rs/redis/`, `ffi/`, `glide-telemetry/`, `glide-logger/`, `glide-rdma/`) are independent crates — each with its own `Cargo.toml`/`Cargo.lock`, no top-level cargo workspace; see [glide-core/AGENTS.md](./glide-core/AGENTS.md) for the crate dependency table.

## Architecture Quick Facts

**Core Implementation:** Rust (`glide-core`) with FFI exposure to language adapters
**Design Constraints:** Async-first APIs, cluster-aware routing, batching support, cross-AZ affinity
**Key Features:** Multi-slot command handling, PubSub auto-reconnection, cluster scan, OpenTelemetry integration

### RESP2/RESP3 Response Normalization

Valkey supports two wire protocols (RESP2 and RESP3) that may return structurally different responses for the same commands. For example, RESP2 returns flat arrays where RESP3 returns maps, and RESP2 returns bulk strings where RESP3 returns typed doubles.

The Rust core normalizes these differences in `glide-core/src/client/value_conversion.rs` so that language bindings receive a consistent data structure regardless of protocol version; language bindings should *not* need to handle RESP2/RESP3 differences themselves. When adding a new command whose RESP2 and RESP3 responses differ, add or reuse an `ExpectedReturnType` variant and implement the conversion logic in `convert_to_expected_type`.

**Supported Engine Versions:** see the table in [README.md](./README.md#supported-engine-versions). The authoritative CI test matrix is `.github/json_matrices/engine-matrix.json`, which may test pre-release engine versions ahead of the README table. Don't duplicate the version table here — it drifts.

## Test Servers

Unit and integration test targets (`make *-test` except modules, `./gradlew :integTest:test` without endpoint flags, `npm test`, `python3 dev.py test`) **auto-start** standalone and cluster Valkey/Redis servers via `utils/cluster_manager.py` and tear them down after — you don't start anything manually.

**Module tests are the exception:** `make modules-test` / `:integTest:modulesTest` / `npm run test:modules` require module-enabled servers that `cluster_manager.py` does not provide, so they take **mandatory** `--cluster-endpoints=`/`--standalone-endpoints=` flags pointing at servers you (or CI, via Docker) started separately.

For ad-hoc/manual testing or pointing a run at a long-lived cluster, drive the harness directly:

```bash
# From utils/, start a standalone server (prints the port and config dir)
python3 cluster_manager.py start

# Start a cluster (default 3 shards, 1 replica each)
python3 cluster_manager.py start --cluster-mode

# Stop a server/cluster you started (by the folder printed at start)
python3 cluster_manager.py stop --cluster-folder <folder-printed-by-start>
```

Then pass the printed endpoint(s) via that binding's endpoint flag (see each binding's AGENTS.md). Requires a `valkey-server` (or `redis-server`) binary on `PATH`.

## Build and Test Rules

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
make go-test       # Run Go integration tests (go/ integ-test; needs a valkey-server on PATH)

# Linting
make java-lint     # Run Java spotlessApply
make python-lint   # Run Python linters via dev.py
make node-lint     # Run Node.js linters
make go-lint       # Run Go linters

# Formatting (Markdown/YAML/JSON via prettier)
make prettier-check
make prettier-fix

# Utilities
make check-valkey-server  # Verify valkey-server (or redis-server) is on PATH; tests need one
make clean                # Remove .build/ directory
make help                 # List available targets
```

There is no `make rust`/`make rust-test`/`make rust-lint` — the Rust crates have no top-level Make integration. Use the raw cargo commands below from each crate's own directory.

### Raw Equivalents Per Stack

**Rust — native client (`rust/`):**

See [rust/AGENTS.md](./rust/AGENTS.md) for the full reference. Summary:

```bash
cd rust
cargo build --release
cargo unit-tests         # alias (rust/.cargo/config.toml) for: cargo test --lib
cargo doc-tests          # alias for: cargo test --doc
cargo integration-tests  # alias for: cargo test --test it_*  (needs a Valkey/Redis server; see Test Servers above)
cargo clippy --all-features --all-targets -- -D warnings
cargo fmt --all -- --check
```

**Rust — core and shared crates (`glide-core/`, `ffi/`, `glide-telemetry/`, `glide-logger/`):**

See [glide-core/AGENTS.md](./glide-core/AGENTS.md) for per-crate test invocations (several require `-- --test-threads=1`) and the Miri UB tests under `ffi/miri-tests/`. The same lint gate below runs in each of `rust/`, `glide-core/`, `ffi/`, `glide-telemetry/`, `glide-logger/`, `benchmarks/rust/`, and the embedded Rust glue in `java/`, `node/rust-client/`, `python/glide-async/` (Go has no Rust directory of its own; its CI re-checks `ffi/` directly). The one exception is `glide-core/redis-rs/redis/` (the vendored engine fork), which uses a lighter gate — see glide-core/AGENTS.md.

```bash
cargo fmt --all -- --check
cargo clippy --all-features --all-targets -- -D warnings
cargo clippy --all-targets -- -D warnings   # second pass with default features (not --all-features)
cargo install --locked cargo-deny && cargo deny --config "$(git rev-parse --show-toplevel)/deny.toml" check
RUSTDOCFLAGS="-Dwarnings" cargo doc --no-deps --document-private-items
```

> `glide-logger/` was renamed from `logger_core/` in PR #7178 — update any older local script that still references `logger_core`.

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
./gradlew :client:test --tests '*.latencyHistory*' # Run unit tests with a pattern (exact '*.latencyHistory' matches 0 tests — method names are suffixed, e.g. latencyHistory_returns_success)

# Integration tests
./gradlew :integTest:test                               # Run all integration tests
./gradlew :integTest:test --tests 'SharedCommandTests'  # Run integration tests from a class
./gradlew :integTest:test --tests '*.latencyHistory*'   # Run integration tests with a pattern
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
npm ci                 # needs the committed package-lock.json
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
make install-tools  # First time / after a toolchain bump: protoc-gen-go, cbindgen, linters
make build
make unit-test
make integ-test
make lint
```

**Benchmarks:**

```bash
# Rust benchmarks
cd glide-core && cargo bench

# Cross-language benchmarks
cd benchmarks && ./install_and_test.sh
```

**Test Results:** Stored in language-specific directories (`target/`, `build/`, `node_modules/`, etc.)

> **Memory-constrained machines:** the native **link** phase (Rust/FFI, and the builds that fork it) is the real memory spike, not compilation — several parallel linkers can each take ~1–2 GB and briefly exhaust RAM. On a low-memory box (e.g. ~16 GB), cap link parallelism and don't build multiple bindings at once: `cargo build -j2` or `export CARGO_BUILD_JOBS=2` for Rust/FFI, `./gradlew <task> --max-workers=2` for Java (then `./gradlew --stop` to drop the daemon), and `make -j2` for the make-based native builds — never `-j$(nproc)`. Machines with ample RAM don't need these caps.

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
- Follow the organization-level [SECURITY.md](https://github.com/valkey-io/.github/blob/main/SECURITY.md) for vulnerability reporting (there is no repository-level copy)
- Run lint/format targets before committing
- Maintain compatibility with supported engine versions
- Do not modify genuinely vendored or third-party code. Note `glide-core/redis-rs/` is **not** in this category — it's a GLIDE-owned fork of redis-rs 0.25.2 (renamed `glide-core-engine`) that the repo deliberately patches; changes there are allowed but check for upstream drift first (see glide-core/AGENTS.md).
- Rust crates must pass `cargo deny check` (see Rust lint commands above)

## Project Structure (Essential)

```text
valkey-glide/
├── .github/workflows/   # CI/CD pipelines
├── benchmarks/          # Performance benchmarks
├── docs/                # Internal docs (published docs: valkey-glide-docs repo)
├── examples/            # Usage examples per language
├── rust/                # Native Rust client (preview)
├── glide-core/          # Core Rust implementation (incl. redis-rs/ vendored fork)
├── ffi/                 # Foreign Function Interface layer (used by go/, both python clients)
├── glide-telemetry/     # OpenTelemetry integration crate
├── glide-logger/        # Shared logging infrastructure
├── glide-rdma/          # RDMA transfer layer (libfabric)
├── go/                  # Go client bindings
├── java/                # Java client bindings (Gradle)
├── node/                # Node.js/TypeScript bindings (npm)
├── python/              # Python async/sync bindings
├── deny.toml            # cargo-deny config, shared by all Rust crates
├── utils/               # Cluster management and utilities
└── Makefile            # Top-level build orchestration
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: the touched binding's build target succeeds (e.g. `make java`/`make node`; see each binding's AGENTS.md), `cargo build --release` succeeds (touched Rust crates)
- [ ] Lint passes: `make *-lint` succeeds (bindings), the Rust lint gate above succeeds (touched Rust crates)
- [ ] Tests pass: `make *-test` succeeds (bindings), the crate's documented test invocation succeeds (touched Rust crates — see glide-core/AGENTS.md and rust/AGENTS.md for feature flags and thread-count requirements)
- [ ] No generated outputs committed (check `.gitignore`)
- [ ] DCO signoff present: `git log --format="%B" -n 1 | grep "Signed-off-by"`
- [ ] Conventional commit format used
- [ ] Cross-language API consistency maintained
- [ ] Security scan passes (no secrets committed)

## Quick Facts for Reasoners

**Engines Supported:** see [README.md](./README.md#supported-engine-versions)
**Key Features:** AZ Affinity, PubSub auto-reconnection, sharded PubSub, cluster-aware multi-key commands, cluster scan, batching (pipeline/transaction), OpenTelemetry integration
**Architecture:** Rust core with FFI bindings, async-first design, cluster and standalone support
**Performance:** Optimized for high throughput and low latency with connection pooling

## Guidelines

Follow these development guidelines when applicable:

- **Rust:** [Options](./rust/docs/dev/options.md)

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Contributing:** [CONTRIBUTING.md](./CONTRIBUTING.md), [PICKING_UP_ISSUES.md](./PICKING_UP_ISSUES.md), [CREATING_ISSUES.md](./CREATING_ISSUES.md), [TRIAGING_ISSUES.md](./TRIAGING_ISSUES.md), [SUBMITTING_PRS.md](./SUBMITTING_PRS.md), [REVIEWING_PRS.md](./REVIEWING_PRS.md)
- **Agent constraints:** [CLAUDE.md](./CLAUDE.md)
- **Security:** [SECURITY.md](https://github.com/valkey-io/.github/blob/main/SECURITY.md)
- **Documentation:** [valkey-io/valkey-glide-docs](https://github.com/valkey-io/valkey-glide-docs) (published docs; in-repo `docs/` holds internal docs)
- **Examples:** [examples/](./examples/)
- **Language-Specific Guides:**
  - [Rust Client Guide](./rust/AGENTS.md) ([Rust Developer Guide](./rust/DEVELOPER.md))
  - [Java Guide](./java/AGENTS.md) ([Java Developer Guide](./java/DEVELOPER.md))
  - [Python Guide](./python/AGENTS.md) ([Python Developer Guide](./python/DEVELOPER.md))
  - [Node.js Guide](./node/AGENTS.md) ([Node.js Developer Guide](./node/DEVELOPER.md))
  - [Go Guide](./go/AGENTS.md) ([Go Developer Guide](./go/DEVELOPER.md))
  - [glide-core / Rust Core Guide](./glide-core/AGENTS.md) ([glide-core Developer Guide](./glide-core/DEVELOPER.md))
  - [FFI crate notes](./ffi/DEVELOPER.md) (GLIDE_NAME/GLIDE_VERSION build variables for Go and Python)
