# AGENTS: Go Client Context for Agentic Tools

This file provides AI agents with the minimum but sufficient context to work productively with the Valkey GLIDE Go client — build commands, testing, and Go-specific guardrails. It builds on [../AGENTS.md](../AGENTS.md) (repo-wide conventions, DCO, commit format, test servers) — read that first.

## Repository Overview

This is the Go client binding for Valkey GLIDE, providing both standalone and cluster client implementations. The Go wrapper communicates with the Rust core via CGO using protobuf protocol and shared C objects.

**Primary Languages:** Go, Rust (CGO bindings)
**Build System:** GNU Make with Go modules
**Architecture:** Go wrapper around Rust FFI core with CGO bindings

**Key Components:**
- `./` - Main Go client library (`glide_client.go`, `glide_cluster_client.go`, `base_client.go`; `*_test.go` here hold the runnable examples)
- `interfaces/` - Public client and command interfaces (BaseClientCommands, GlideClientCommands, etc.)
- `config/`, `options/`, `models/`, `pipeline/`, `constants/` - Public config, command options, result types, batches
- `servermodules/` - JSON and FT (vector search) module commands
- `integTest/` - Integration tests and test suites
- `benchmarks/` - Performance benchmarking tool (separate Go module)
- `internal/` - Internal packages including generated protobuf code
- `rustbin/` - Platform-specific Rust static libraries (generated)
- `examples/examples.md` - Index of the example files

## Architecture Quick Facts

**Core Implementation:** Go wrapper around glide-core Rust library via CGO
**Client Types:** Client (standalone), ClusterClient (cluster)
**API Style:** Context-aware with standard Go patterns
**Protocol:** Protobuf communication with Rust core via shared C objects

**Supported Platforms:**
- Linux: Ubuntu 20+, Amazon Linux 2/2023 (x86_64, aarch64)
- macOS: 13.7+ (x86_64), 14.7+ (aarch64)
- **Note:** Alpine Linux/MUSL supported with `musl` build tag

**Go Versions:** 1.22+ (`go.mod`; CI runs 1.22 and 1.24)
**Module:** `github.com/valkey-io/valkey-glide/go/v2`

## Build and Test Rules (Agents)

<!-- These commands are intentionally mirrored in go/DEVELOPER.md (human guide). Keep both in sync when the build/test/lint commands change. -->

### Preferred (Make Targets)
```bash
# Build commands
make build                              # Build release version
make build-debug                        # Build debug version with symbols
make clean                              # Clean build artifacts

# Development setup
make install-build-tools                # Install protoc-gen-go, cbindgen
make install-dev-tools                  # Install linters and test tools
make install-tools                      # Install all tools

# Testing
make unit-test                          # Unit tests only
make example-test                       # Runnable examples
make integ-test                         # Integration tests (excludes modules)
make modules-test                       # Module-specific tests
make pubsub-test                        # PubSub functionality tests
make long-timeout-test                  # Tests with extended timeouts
make opentelemetry-test                 # OpenTelemetry integration tests

# Code quality
make lint                               # Run all linters
make lint-ci                            # CI-compatible lint (fails on issues)
make format                             # Auto-fix formatting issues

# Code generation
make generate-protobuf                  # Regenerate protobuf files
```

### Raw Equivalents
```bash
# Manual build (from go/)
go build ./...
cd benchmarks && go build -ldflags="-w" ./...

# Manual test execution
go test -v ./... -skip 'Example|TestGlideTestSuite|TestPool|TestScope'   # matches `make unit-test`
# NOTE: integ-tests run under AddressSanitizer in the Makefile: CC="gcc -fsanitize=address" go test -v ./integTest/... -skip 'TestGlideTestSuite/TestModule' (needs gcc; disabled on musl; the skip matches make integ-test, whose default servers have no modules)

# Manual linting
go vet ./...
staticcheck ./...
gofumpt -d .
golines --dry-run --shorten-comments -m 127 .

# Manual formatting
gofumpt -w .
golines -w --shorten-comments -m 127 .

# Manual protobuf generation (the .proto files carry no go_package, so the M mappings are
# required; the Makefile also rm -rf's internal/protobuf first). Prefer `make generate-protobuf`.
protoc --proto_path=../glide-core/src/protobuf \
  --go_opt=Mconnection_request.proto=github.com/valkey-io/valkey-glide/go/v2/internal/protobuf \
  --go_opt=Mcommand_request.proto=github.com/valkey-io/valkey-glide/go/v2/internal/protobuf \
  --go_opt=Mresponse.proto=github.com/valkey-io/valkey-glide/go/v2/internal/protobuf \
  --go_opt=paths=source_relative --go_out=./internal/protobuf \
  ../glide-core/src/protobuf/*.proto
```

### Test Servers

`make integ-test`, `make pubsub-test` etc. **auto-start** standalone + cluster servers via `utils/cluster_manager.py` and stop them afterward — nothing to start manually. **`make modules-test` is the exception:** it needs module-enabled servers `cluster_manager.py` doesn't provide, so `standalone-endpoints=`/`cluster-endpoints=` are **mandatory** there (CI starts them via Docker). The endpoint args on the non-module targets are only for pointing at a server you started yourself; see the root [../AGENTS.md](../AGENTS.md) "Test Servers" section. A `valkey-server`/`redis-server` on `PATH` is required either way. Note: the integration suite's `SetupSuite` stops every `cluster*` folder under `utils/clusters/` before starting fresh ones — including clusters left behind by an interrupted earlier run, so a crashed run self-heals on the next invocation but a concurrently-running suite's clusters can be caught too.

### Test Execution Options
```bash
# Run with specific test filter
make integ-test test-filter=TestSet

# Run with pattern matching
make integ-test test-filter="Test\(Set\|Get\)"

# Run with existing endpoints
make integ-test standalone-endpoints=localhost:6379 cluster-endpoints=localhost:7000

# Run with TLS (the suite starts its own servers — passing your own endpoints with TLS is
# not supported; see DEVELOPER.md). The method-name filter selects the Tls-designated tests.
make tls-test tls=true

# Alpine/MUSL builds (note: GOFLAGS=... is an env var — the `:=` form shown in some
# notes is Makefile assignment syntax and will NOT work in a shell)
export GOFLAGS=-tags=musl
make build
```

### Cross-Compilation Support
```bash
# Linux AMD64 (via Docker)
docker run --rm -v "$PWD":/app -w /app golang:1.22 \
  bash -c "CGO_ENABLED=1 GOOS=linux GOARCH=amd64 go build -o myapp-linux-amd64 ./..."

# Linux ARM64 (via Docker)
docker run --rm -v "$PWD":/app -w /app golang:1.22 \
  bash -c "CGO_ENABLED=1 GOOS=linux GOARCH=arm64 CC=aarch64-linux-gnu-gcc go build -o myapp-linux-arm64 ./..."

# macOS (on macOS system)
CGO_ENABLED=1 GOOS=darwin GOARCH=amd64 go build -o myapp-darwin-amd64 ./...
CGO_ENABLED=1 GOOS=darwin GOARCH=arm64 go build -o myapp-darwin-arm64 ./...
```

## Contribution Requirements

DCO signoff and Conventional Commits are required repo-wide — see [../AGENTS.md](../AGENTS.md#contribution-requirements) for the exact commands. Use the `go` scope, e.g. `feat(go): implement cluster scan with context support`.

### Code Quality Requirements

**Go Linters (via Make):**
```bash
make lint                              # Must pass before commit
make format                            # Auto-fix formatting issues
```

**Individual Tools:**
- `go vet` - Go static analysis
- `staticcheck` - Advanced static analysis
- `gofumpt` - Stricter gofmt formatting
- `golines` - Line length enforcement (127 chars)

**License Headers:**
All `.go` files must include: `// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0`

**Rust Components** (Go has no Rust crate of its own — CI lints the `ffi/` crate it depends on via the shared Rust gate; there is no `go/Cargo.toml`):
```bash
# From ffi/ — these are the common Rust checks; the full repo-wide gate
# (fmt --check, clippy ×2, cargo deny, cargo doc) is in ../AGENTS.md. Run it there.
cargo fmt --all -- --check
cargo clippy --all-features --all-targets -- -D warnings
```

## Guardrails & Policies

### Generated Outputs (Never Commit)
- `rustbin/` - Platform-specific Rust static libraries
- `internal/protobuf/` - Generated protobuf Go files
- `lib.h` - Generated C header file
- `reports/` - Test reports and coverage
- `benchmarks/benchmarks` - Compiled benchmark binary
- (`make clean` also removes the shared `ffi/target/` outside `go/` — note it wipes the Rust cache other bindings/CI reuse)

### Go-Specific Rules
- **Go 1.22+ Required:** Minimum runtime and build version
- **CGO Enabled:** Required for Rust FFI integration
- **Context Support:** All client methods accept `context.Context`
- **Error Handling:** Follow Go error handling conventions
- **Naming Conventions:** Use PascalCase for exported functions (e.g., `BZPopMin`, `SetWithOptions`)
- **MUSL Support:** Use `-tags=musl` for Alpine Linux builds
- **Cross-Compilation:** Use Docker for Linux targets, native builds for macOS

> **Environment gotcha (not universal):** On some Linux hosts (observed on Amazon Linux 2) a local `go build`/`go test` cgo link fails with `undefined reference to 'dlsym'` (the Rust static lib references `dlsym` via getrandom). If you hit it, prepend `CGO_LDFLAGS=-ldl`. CI does not set this, so most environments do not need it — treat it as a troubleshooting step, not a required flag.

### Documentation Standards
- **Runnable Examples:** All examples must compile and execute successfully
- **Example Naming:** `ExampleClient_<FunctionName>` or `ExampleClusterClient_<FunctionName>`
- **Links:** Use `[text]: url` format for external references
- **Type References:** Use `[Package.Type]` for internal type links
- **Output Directive:** Include expected output in examples

## Project Structure (Essential)

```
go/                             # Main Go client library
├── glide_client.go             # Standalone client implementation
├── glide_cluster_client.go     # Cluster client implementation
├── interfaces/                 # Public client and command interfaces
├── integTest/                  # Integration test suites
├── benchmarks/                 # Performance benchmarking tool
├── internal/                   # Internal packages
│   ├── protobuf/               # Generated protobuf files
│   └── utils/                  # Internal utilities
├── rustbin/                    # Platform-specific Rust libraries (generated)
├── examples/                   # Usage examples and documentation
├── Makefile                    # Build and test orchestration
├── go.mod                      # Go module definition
└── go.sum                      # Go module checksums
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: `make build` succeeds
- [ ] All tests pass: `make unit-test integ-test` succeeds
- [ ] Linting passes: `make lint` succeeds
- [ ] Formatting correct: `make format` produces no changes
- [ ] Examples work: `make example-test` succeeds
- [ ] License headers present in all `.go` files
- [ ] No generated outputs committed (check `.gitignore`)
- [ ] DCO signoff present: `git log --format="%B" -n 1 | grep "Signed-off-by"`
- [ ] Conventional commit format used
- [ ] Context support added to new client methods
- [ ] Runnable examples provided for new public APIs

## Quick Facts for Reasoners

**Module:** `github.com/valkey-io/valkey-glide/go/v2` on Go modules
**API Style:** Context-aware, standard Go patterns with proper error handling
**Client Types:** Client (standalone), ClusterClient (cluster)
**Key Features:** CGO-based Rust integration, cross-compilation support, comprehensive benchmarking
**Testing:** Unit tests, integration tests, runnable examples, module tests, PubSub tests
**Platforms:** Linux (glibc/musl), macOS (Intel/Apple Silicon)
**Dependencies:** Go 1.22+, Rust toolchain, protobuf compiler, CGO-compatible C compiler

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Development Setup:** [DEVELOPER.md](./DEVELOPER.md)
- **Examples:** [examples/examples.md](./examples/examples.md)
- **API Documentation:** Generated via `pkgsite` or `go doc`
- **Integration Tests:** [integTest/](./integTest/) directory
- **Benchmarks:** [benchmarks/](./benchmarks/) directory
- **Cross-Compilation Guide:** [README.md#cross-compilation-guide](./README.md#cross-compilation-guide)
- **Command Interfaces:** [interfaces/](./interfaces/) directory
