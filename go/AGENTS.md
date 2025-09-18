# AGENTS: Go Client Context for Agentic Tools

This file provides AI agents with the minimum but sufficient context to work productively with the Valkey GLIDE Go client. It covers build commands, testing, contribution requirements, and essential guardrails specific to the Go implementation.

## Repository Overview

This is the Go client binding for Valkey GLIDE, providing both standalone and cluster client implementations. The Go wrapper communicates with the Rust core via CGO using protobuf protocol and shared C objects.

**Primary Languages:** Go, Rust (CGO bindings)
**Build System:** GNU Make with Go modules
**Architecture:** Go wrapper around Rust FFI core with CGO bindings

**Key Components:**
- `./` - Main Go client library and interfaces
- `integTest/` - Integration tests and test suites
- `benchmarks/` - Performance benchmarking tool
- `internal/` - Internal packages including protobuf definitions
- `rustbin/` - Platform-specific Rust static libraries
- `examples/` - Usage examples and documentation

## Architecture Quick Facts

**Core Implementation:** Go wrapper around glide-core Rust library via CGO
**Client Types:** Client (standalone), ClusterClient (cluster)
**API Style:** Context-aware with standard Go patterns
**Protocol:** Protobuf communication with Rust core via shared C objects

**Supported Platforms:**
- Linux: Ubuntu 20+, Amazon Linux 2/2023 (x86_64, aarch64)
- macOS: 13.7+ (x86_64), 14.7+ (aarch64)
- **Note:** Alpine Linux/MUSL supported with `musl` build tag

**Go Versions:** 1.22+
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
go test -v ./... -skip 'Example|TestGlideTestSuite'

# Manual linting
go vet ./...
staticcheck ./...
gofumpt -d .
golines --dry-run --shorten-comments -m 127 .

# Manual formatting
gofumpt -w .
golines -w --shorten-comments -m 127 .

# Manual protobuf generation
protoc --proto_path=../glide-core/src/protobuf --go_out=./internal/protobuf ../glide-core/src/protobuf/*.proto
```

### Test Execution Options
```bash
# Run with specific test filter
make integ-test test-filter=TestSet

# Run with pattern matching
make integ-test test-filter="Test\(Set\|Get\)"

# Run with existing endpoints
make integ-test standalone-endpoints=localhost:6379 cluster-endpoints=localhost:7000

# Run with TLS
make integ-test standalone-endpoints=localhost:6379 cluster-endpoints=localhost:7000 tls=true

# Alpine/MUSL builds
export GOFLAGS := -tags=musl
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
```bash
# From go/ directory
rustup component add clippy rustfmt
cargo clippy --all-features --all-targets -- -D warnings
cargo fmt --manifest-path ./Cargo.toml --all
```

## Guardrails & Policies

### Generated Outputs (Never Commit)
- `rustbin/` - Platform-specific Rust static libraries
- `internal/protobuf/` - Generated protobuf Go files
- `lib.h` - Generated C header file
- `reports/` - Test reports and coverage
- `benchmarks/benchmarks` - Compiled benchmark binary
- `target/` - Rust build artifacts (if present)

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
go/
├── api/                        # Main Go client library
│   ├── client.go               # Standalone client implementation
│   ├── cluster_client.go       # Cluster client implementation
│   └── commands/               # Command interfaces and implementations
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
- **Command Interfaces:** [api/commands/](./api/commands/) directory
