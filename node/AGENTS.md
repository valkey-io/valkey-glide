# AGENTS: Node.js Client Context for Agentic Tools

This file provides AI agents and developers with the minimum but sufficient context to work productively with the Valkey GLIDE Node.js client. It covers build commands, testing, contribution requirements, and essential guardrails specific to the Node.js/TypeScript implementation.

## Repository Overview

This is the Node.js/TypeScript client binding for Valkey GLIDE, providing both standalone and cluster client implementations. The Node.js wrapper communicates with the Rust core via NAPI-RS using protobuf protocol.

**Primary Languages:** TypeScript, Rust (NAPI bindings)
**Build System:** npm with TypeScript compiler
**Architecture:** TypeScript wrapper around Rust FFI core with NAPI-RS bindings

**Key Components:**
- `src/` - Main TypeScript client library
- `rust-client/` - Rust NAPI bindings and FFI integration
- `build-ts/` - Compiled TypeScript output
- `tests/` - Test suites and utilities
- `pm-and-types-tests/` - Package manager and TypeScript types testing

## Architecture Quick Facts

**Core Implementation:** TypeScript wrapper around glide-core Rust library via NAPI-RS
**Client Types:** GlideClient (standalone), GlideClusterClient (cluster)
**API Style:** Async-first with Promise return types
**Protocol:** Protobuf communication with Rust core

**Supported Platforms:**
- Linux: glibc 2.17+, musl libc 1.2.3+ (Alpine)
- macOS: 13.5+ (x86_64), 15.0+ (aarch64/Apple Silicon)
- Node.js: 16+ (npm 11+ recommended for Linux)

**Package:** `@valkey/valkey-glide` on npm

## Build and Test Rules (Agents)

### Preferred (npm Scripts)
```bash
# Build commands
npm run build                    # Development build (fast, unoptimized)
npm run build:release           # Release build (optimized, stripped debug symbols)
npm run build:benchmark         # Benchmark build (optimized with debug symbols)

# Incremental builds
npm run build:ts                # TypeScript only
npm run build:rust-client       # Rust client only
npm run build-protobuf          # Protobuf generation only

# Testing
npm test                        # Standard tests (excludes server modules)
npm run test:debug              # Tests with debugging options
npm run test:minimum            # Minimal test suite (faster)
npm run test:modules            # Server module tests only

# Linting and Formatting
npm run lint                    # Check code quality (ESLint + Prettier)
npm run lint:fix                # Auto-fix linting issues
npm run prettier:check          # Check formatting only
npm run prettier:format         # Format code automatically

# Development utilities
npm run repl                    # Interactive TypeScript REPL
npm run clean:build             # Clean build artifacts only
npm run clean                   # Complete cleanup including node_modules
```

### Raw Equivalents
```bash
# Manual TypeScript compilation
npx tsc

# Manual protobuf generation
mkdir -p build-ts
pbjs -t static-module -w commonjs --no-verify --no-convert -o build-ts/ProtobufMessage.js ../glide-core/src/protobuf/*.proto
pbts -o build-ts/ProtobufMessage.d.ts build-ts/ProtobufMessage.js

# Manual Rust build (from rust-client/)
cargo build --release

# Manual test execution
npx jest --verbose

# Manual linting
npx eslint -c ../eslint.config.mjs
npx prettier --check .
```

### Test Execution Options
```bash
# Run with existing endpoints
npm run test:debug -- --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379

# Run with TLS
npm run test:debug -- --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379 --tls=true

# Run specific test pattern
npm run test:debug -- --testNamePattern="batch"

# Run server modules tests
npm run test:modules -- --cluster-endpoints=localhost:7000
```

## Contribution Requirements

### Developer Certificate of Origin (DCO) Signoff REQUIRED

All commits must include a `Signed-off-by` line:

```bash
# Add signoff to new commits
git commit -s -m "feat(node): add new command implementation"

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

**Example:** `feat(node): implement cluster scan with routing options`

### Code Quality Requirements

**ESLint + Prettier:**
```bash
npm run lint                    # Must pass before commit
npm run lint:fix                # Fix linting and formatting issues
```

**TypeScript Compilation:**
```bash
npm run build:ts                # Must compile without errors
```

**Rust Components:**
```bash
# From rust-client/ directory
cargo clippy --all-features --all-targets -- -D warnings
cargo fmt --manifest-path ./Cargo.toml --all
```

## Guardrails & Policies

### Generated Outputs (Never Commit)
- `build-ts/` - Compiled TypeScript output
- `node_modules/` - Node.js dependencies
- `rust-client/node_modules/` - Rust client dependencies
- `rust-client/target/` - Rust build artifacts
- `rust-client/valkey-glide.*.node` - Native binaries
- `src/valkey-glide.*.node` - Native binaries
- `build/*.tsbuildinfo` - TypeScript build info
- `*.tsbuildinfo` - TypeScript incremental build files
- `test-report*.html` - Test reports
- `glide-logs/` - Runtime logs
- `package-lock.json`, `yarn.lock` - Lock files (project uses npm)

### Node.js-Specific Rules
- **Node.js 16+ Required:** Minimum runtime version
- **npm 11+ Recommended:** For Linux users (better libc support)
- **Promise-based APIs:** All client methods return Promises
- **Resource Management:** Call `client.close()` to cleanup connections
- **Protobuf Updates:** Run `npm run build-protobuf` after proto changes
- **NAPI Bindings:** Native module built per platform/architecture

### TypeScript Guidelines
- Maintain strict TypeScript configuration
- Export all public APIs through `index.ts`
- Use proper type definitions for all public interfaces
- Test TypeScript declarations with package manager tests

## Project Structure (Essential)

```
node/
├── src/                        # TypeScript client implementation
│   ├── GlideClient.ts          # Standalone client
│   ├── GlideClusterClient.ts   # Cluster client
│   └── index.ts                # Main exports
├── rust-client/                # Rust NAPI bindings
│   ├── src/                    # Rust FFI implementation
│   └── Cargo.toml              # Rust dependencies
├── build-ts/                   # Compiled TypeScript (generated)
├── tests/                      # Test suites
├── pm-and-types-tests/         # Package manager compatibility tests
├── package.json                # npm configuration and scripts
├── tsconfig.json               # TypeScript configuration
└── jest.config.js              # Jest test configuration
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: `npm run build:release` succeeds
- [ ] All tests pass: `npm test` succeeds
- [ ] Linting passes: `npm run lint` succeeds
- [ ] TypeScript compiles: `npm run build:ts` succeeds
- [ ] Rust components build: `cd rust-client && cargo build --release` succeeds
- [ ] No generated outputs committed (check `.gitignore`)
- [ ] DCO signoff present: `git log --format="%B" -n 1 | grep "Signed-off-by"`
- [ ] Conventional commit format used
- [ ] TypeScript types properly exported
- [ ] Package manager compatibility maintained
- [ ] Native binaries not committed

## Quick Facts for Reasoners

**Package:** `@valkey/valkey-glide` on npm registry
**API Style:** Promise-based async, modern ES modules support
**Client Types:** GlideClient (standalone), GlideClusterClient (cluster)
**Key Features:** TypeScript support, NAPI-RS native bindings, protobuf communication
**Testing:** Jest test framework, interactive REPL, package manager compatibility tests
**Platforms:** Linux (glibc/musl), macOS (Intel/Apple Silicon)
**Dependencies:** Node.js 16+, protobufjs, long, platform-specific native binaries

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Development Setup:** [DEVELOPER.md](./DEVELOPER.md)
- **Examples:** [../examples/node/](../examples/node/)
- **API Documentation:** [Valkey GLIDE Node.js docs](https://valkey.io/valkey-glide/node/)
- **Wiki:** [NodeJS wrapper wiki](https://github.com/valkey-io/valkey-glide/wiki/NodeJS-wrapper)
- **Test Suites:** [tests/](./tests/) directory
- **Package Manager Tests:** [pm-and-types-tests/](./pm-and-types-tests/)
- **Rust Client:** [rust-client/](./rust-client/) directory
