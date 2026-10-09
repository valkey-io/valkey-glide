# AGENTS: Go Client Context for Agentic Tools

This file provides AI agents with the minimum but sufficient context to work productively with the Valkey GLIDE Go client. It covers build commands, testing, contribution requirements, and essential guardrails specific to the Go implementation.

## Repository Overview

This is the Go client binding for Valkey GLIDE, providing both standalone and cluster client implementations. The Go wrapper communicates with the Rust core via CGO using protobuf protocol and shared C objects.

**Primary Languages:** Go, Rust (CGO bindings)
**Build System:** GNU Make with Go modules
**Architecture:** Go wrapper around Rust FFI core with CGO bindings

**Key Components:**

- `./` - Main Go client library; the `*_test.go` files here hold the runnable examples
- `interfaces/` - Public client and command interfaces (BaseClientCommands, GlideClientCommands, etc.)
- `config/`, `options/`, `models/`, `pipeline/`, `constants/` - Public configuration, command options, result types, batches
- `servermodules/` - JSON and FT (vector search) module commands
- `integTest/` - Integration tests and test suites
- `benchmarks/` - Performance benchmarking tool (separate module)
- `internal/` - Internal packages including generated protobuf code
- `rustbin/` - Platform-specific Rust static libraries (generated)
- `examples/examples.md` - Index of the example files

## Architecture Quick Facts

**Core Implementation:** Go wrapper around glide-core Rust library via CGO
**Client Types:** Client (standalone), ClusterClient (cluster)
**API Style:** Context-aware with standard Go patterns
**Protocol:** Protobuf communication with Rust core via shared C objects

**Supported Platforms:** see [README.md#system-requirements](./README.md#system-requirements). Alpine Linux/MUSL needs the `musl` build tag (see `options/cgo_flags.go`); the Makefile sets it automatically on Alpine.

**Go Versions:** 1.22+ (`go.mod`; CI runs 1.22 and 1.24)
**Module:** `github.com/valkey-io/valkey-glide/go/v2`

## Build and Test Rules (Agents)

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
make unit-test                          # Unit tests only (no server needed)
make example-test                       # Runnable examples (starts and stops its own servers)
make integ-test                         # Integration tests (excludes modules)
make modules-test                       # Module-specific tests
make pubsub-test                        # PubSub functionality tests
make tls-test tls=true                  # TLS-designated tests (method names containing Tls)
make long-timeout-test                  # Tests with extended timeouts
make opentelemetry-test                 # OpenTelemetry integration tests

# Code quality
make lint                               # go vet, staticcheck, gofumpt, golines, license headers
make lint-ci                            # CI-compatible lint (fails on issues)
make format                             # Auto-fix formatting issues

# Code generation
make generate-protobuf                  # Regenerate internal/protobuf from glide-core/src/protobuf
```

### Raw Equivalents

```bash
# Manual build (from go/)
go build ./...
cd benchmarks && go build -ldflags="-w" ./...

# Manual test execution (same skip list as `make unit-test`)
go test -v ./... -skip 'Example|TestGlideTestSuite|TestPool|TestScope'

# Manual linting
go vet ./...
staticcheck ./...
gofumpt -d .
golines --dry-run --shorten-comments -m 127 .

# Manual formatting
gofumpt -w .
golines -w --shorten-comments -m 127 .

# Manual protobuf generation (the .proto files carry no go_package, so the M mappings are required)
protoc --proto_path=../glide-core/src/protobuf \
  --go_opt=Mconnection_request.proto=github.com/valkey-io/valkey-glide/go/v2/internal/protobuf \
  --go_opt=Mcommand_request.proto=github.com/valkey-io/valkey-glide/go/v2/internal/protobuf \
  --go_opt=Mresponse.proto=github.com/valkey-io/valkey-glide/go/v2/internal/protobuf \
  --go_opt=paths=source_relative --go_out=./internal/protobuf \
  ../glide-core/src/protobuf/*.proto
```

### Test Execution Options

```bash
# Run with specific test filter
make integ-test test-filter=TestSet

# Run with pattern matching
make integ-test test-filter="Test\(Set\|Get\)"

# Run with existing endpoints
make integ-test standalone-endpoints=localhost:6379 cluster-endpoints=localhost:7000

# Run with TLS (the suite starts its own servers; see DEVELOPER.md for why own endpoints are not supported)
make tls-test tls=true

# Alpine/MUSL builds outside the Makefile
export GOFLAGS=-tags=musl
go build ./...
```

### Cross-Compilation Support

Cross-compiling an application that depends on this module is documented in [README.md#cross-compilation-guide](./README.md#cross-compilation-guide). It does not apply to building this repository itself.

## Contribution Requirements

### Developer Certificate of Origin (DCO) Signoff REQUIRED

All commits must include a `Signed-off-by` line:

```bash
# Add signoff to new commits
git commit -s -m "feat(go): add new command implementation"

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

**Example:** `feat(go): implement cluster scan with context support`

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

**Rust Components:**
The CGO static library is the `glide-ffi` crate in `../ffi/` (see [ffi/README.md](../ffi/README.md)). From `ffi/`:

```bash
rustup component add clippy rustfmt
cargo clippy --all-features --all-targets -- -D warnings
cargo fmt --manifest-path ./Cargo.toml --all
```

## Guardrails & Policies

### Generated Outputs (Never Commit)

Listed in `go/.gitignore`:

- `rustbin/` - Platform-specific Rust static libraries (force-added only by the release workflow)
- `internal/protobuf/*.pb.go` - Generated protobuf Go files
- `lib.h` - cbindgen-generated C header
- `reports/` - Test reports
- `benchmarks/benchmarks`, `benchmarks/results/`, `benchmarks/gobenchmarks.json` - Benchmark binary and results
- `*.test` - Compiled test binaries
- `../ffi/target/` - Rust build artifacts (outside `go/`, ignored by the root `.gitignore`)

### Go-Specific Rules

- **Go 1.22+ Required:** Minimum runtime and build version
- **CGO Enabled:** Required for Rust FFI integration
- **Context Support:** All client methods accept `context.Context`
- **Error Handling:** Follow Go error handling conventions
- **Naming Conventions:** Use PascalCase for exported functions (e.g., `BZPopMin`, `SetWithOptions`)
- **MUSL Support:** Use `-tags=musl` for Alpine Linux builds
- **Cross-Compilation:** Use Docker for Linux targets, native builds for macOS

### Documentation Standards

- **Runnable Examples:** All examples must compile and execute successfully
- **Example Naming:** `ExampleClient_<FunctionName>` or `ExampleClusterClient_<FunctionName>`
- **Links:** Use `[text]: url` format for external references
- **Type References:** Use `[Package.Type]` for internal type links
- **Output Directive:** Include expected output in examples

## Project Structure (Essential)

```
go/                             # Main Go client library
├── base_client.go              # Shared command implementations (CGO entry point)
├── glide_client.go             # Client (standalone)
├── glide_cluster_client.go     # ClusterClient (cluster)
├── *_test.go                   # Runnable examples, grouped by command family
├── interfaces/                 # Public client and command interfaces
├── config/                     # Client configuration
├── options/                    # Per-command option types
├── models/                     # Result types
├── pipeline/                   # Batch (pipeline/transaction) support
├── servermodules/              # glidejson, glideft module commands
├── integTest/                  # Integration test suites
├── benchmarks/                 # Performance benchmarking tool (own go.mod)
├── internal/                   # Internal packages
│   ├── protobuf/               # Generated protobuf files
│   └── utils/                  # Internal utilities
├── rustbin/                    # Platform-specific Rust libraries (generated)
├── examples/examples.md        # Index of the example files
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
- **API Documentation:** `go doc`, or `pkgsite` as described in [DEVELOPER.md](./DEVELOPER.md#function-names-for-examples)
- **Integration Tests:** [integTest/](./integTest/) directory
- **Benchmarks:** [benchmarks/](./benchmarks/) directory
- **Cross-Compilation Guide:** [README.md#cross-compilation-guide](./README.md#cross-compilation-guide)
- **Command Interfaces:** [interfaces/](./interfaces/) directory
- **FFI crate:** [../ffi/README.md](../ffi/README.md)
