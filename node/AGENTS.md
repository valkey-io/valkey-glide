# AGENTS: Node.js Client Context for Agentic Tools

This file provides AI agents and developers with the minimum but sufficient context to work productively with the Valkey GLIDE Node.js client — build commands, testing, and Node.js/TypeScript-specific guardrails. It builds on [../AGENTS.md](../AGENTS.md) (repo-wide conventions, DCO, commit format, test servers) — read that first.

## Repository Overview

This is the Node.js/TypeScript client binding for Valkey GLIDE, providing both standalone and cluster client implementations. The Node.js wrapper communicates with the Rust core via direct NAPI function calls (not socket IPC or protobuf for command flow).

**Primary Languages:** TypeScript, Rust (NAPI bindings)
**Build System:** npm with TypeScript compiler
**Architecture:** TypeScript wrapper around glide-core Rust library via direct NAPI-RS bindings

**Key Components:**

- `src/` - Main TypeScript client library
- `rust-client/` - Rust NAPI bindings and FFI integration
- `build-ts/` - Compiled TypeScript output
- `tests/` - Test suites and utilities
- `pm-and-types-tests/` - Package manager and TypeScript types testing
- `hybrid-node-tests/` - CommonJS/ESM/pnpm consumer smoke tests
- `npm/` - Per-platform native binary packages (`@valkey/valkey-glide-<platform>`)

## Architecture Quick Facts

**Core Implementation:** TypeScript wrapper around glide-core Rust library via NAPI-RS
**Client Types:** GlideClient (standalone), GlideClusterClient (cluster), GlideMonitorClient (`MONITOR` stream), `ClientPool` (client-instance pool), `IsolatedScope` (dedicated connection for WATCH/MULTI/SELECT state) — the last two backed by `rust-client/src/pool.rs`
**API Style:** Async-first with Promise return types
**Command Protocol:** Direct NAPI function calls on `GlideClientHandle` (not protobuf for command flow)

### Command Flow (JS to Rust)

1. JS calls `createLeakedStringVec(args)` to allocate args on the Rust heap, receiving a split pointer `[low, high]`
2. JS calls `clientHandle.sendCommand(callbackIdx, requestType, argsPointerHigh, argsPointerLow, spanPtr?, routeBytes?)` synchronously
3. Rust reclaims the args via `Box::from_raw`, checks inflight limits, builds a `redis::Cmd`, and sends a `WorkerMessage` to the pinned worker thread via `mpsc::unbounded_channel`
4. The worker thread's `spawn_local` task executes the command against glide-core's `Client`
5. Response is pushed to a per-client `ResponseBuffer` (parking_lot mutex-protected `Vec<CommandResponse>`)
6. If the buffer was empty, a `ThreadsafeFunction` wake callback fires to JS (one wake per batch, not per response)
7. JS `handleResponsesAvailable` calls `clientHandle.drainResponses()` to get all pending `CommandResponse` objects
8. For responses with value pointers, JS calls `valueFromSplitPointer(high, low, stringDecoder)` to reclaim and decode the `redis::Value`

Other NAPI entry points: `sendBatch`, `invokeScript`, `clusterScan`, `updateConnectionPassword`, `refreshIamToken`, plus the pool/scope surface in `rust-client/src/pool.rs` (`createPool`, `poolTryAcquire`, `poolAcquireBlocking`, `poolRelease`, `scopeTryAcquire`, `scopeExecute`, …) exposed on the TS side via `src/ClientPool.ts` / `src/IsolatedScope.ts`.

### Worker Pool Architecture

- A global `LocalPoolHandle` (from `tokio_util`) manages `num_cpus` worker threads
- Each client is pinned to a single worker thread via `spawn_pinned` for thread-local command execution
- Commands within a client run concurrently via `task::spawn_local` on that thread
- The pool is reference-counted: created on first client, dropped when last client closes (enables clean Node.js exit)

### Inflight Request Tracking

- An `AtomicIsize` counter initialized to the inflight limit (default 1000)
- `sendCommand` atomically decrements before sending; returns `false` if limit exceeded
- Worker increments the counter after each response is buffered

### Where Protobuf Is Still Used

- **Connection request**: serialized as protobuf bytes, parsed by Rust (`ProtobufConnectionRequest`)
- **Cluster routing**: `Routes` protobuf encoded to bytes in JS, parsed by Rust via `parse_route_bytes`
- **Command type enums**: `RequestType` protobuf enum maps command names to numeric IDs

**Supported Platforms:**

- Linux: glibc 2.17+, musl libc 1.2.3+ (Alpine)
- macOS: 13.5+ (x86_64), 15.0+ (aarch64/Apple Silicon)
- Windows: x86_64 (`x86_64-pc-windows-msvc`; built, published as `win32-x64-msvc`, and tested via a `test-windows` CI job)
- Node.js: 20.12+ (`util.styleText`, used in the release build, landed in 20.12 — older 20.x and all 18.x fail `npm run build:release` with a `styleText` SyntaxError; `package.json` `engines` requires `>=20`). npm 11+ recommended on Linux.

**Package:** `@valkey/valkey-glide` on npm

## Build and Test Rules (Agents)

<!-- These commands are intentionally mirrored in node/DEVELOPER.md (human guide). Keep both in sync when the build/test/lint commands change. -->

### Preferred (npm Scripts)

```bash
# Build commands
npm run build                    # Development build (fast, unoptimized)
npm run build:release           # Release build (optimized, stripped debug symbols)
npm run build:benchmark         # Benchmark build (optimized with debug symbols)

# Incremental builds
npm run build:ts                # TypeScript only (needs a prior full build — src/ imports generated build-ts/ artifacts)
npm run build:rust-client       # Rust client only
npm run build-protobuf          # Protobuf generation only

# Testing
npm test                        # Standard tests (excludes server modules)
npm run test:debug              # All suites incl. ServerModules, serial (--runInBand)
npm run test:minimum            # Serial; skips GlideJson/GlideFt/pubsub/kill tests
npm run test:modules            # Serial; only GlideJson/GlideFt tests (needs a modules-enabled server)

# Linting and Formatting
npm run lint                    # Check code quality (ESLint + Prettier)
npm run lint:fix                # Auto-fix linting issues
npm run prettier:check          # Check formatting only
npm run prettier:format         # Format code automatically

# Development utilities
npm run repl                    # Interactive TypeScript REPL
npm run clean:build             # Clean build artifacts only
npm run clean                   # Full cleanup — WARNING: also deletes the committed lockfiles (package-lock.json, rust-client/{package-lock.json,Cargo.lock}) and node_modules; restore the lockfiles with git before committing
```

### Raw Equivalents

```bash
# Manual TypeScript compilation
npx tsc

# Manual protobuf generation (same three steps `npm run build-protobuf` runs; do not drop the `replace`)
# npx because pbjs/pbts/replace are devDependencies, not global binaries
mkdir -p build-ts
npx pbjs -t static-module -w commonjs --no-verify --no-convert -o build-ts/ProtobufMessage.js ../glide-core/src/protobuf/*.proto
npx pbts -o build-ts/ProtobufMessage.d.ts build-ts/ProtobufMessage.js
npx replace "this\.encode\(message, writer\)\.ldelim" "this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim" build-ts/ProtobufMessage.js

# Manual Rust build (from rust-client/)
cargo build --release

# Manual test execution
npx jest --verbose

# Manual linting
npx eslint -c ../eslint.config.mjs
npx prettier --check .
```

### Test Servers

`npm test` and the unit/integration run **auto-start** standalone + cluster servers via `utils/cluster_manager.py` and stop them afterward — nothing to start manually. **`npm run test:modules` is the exception:** it needs module-enabled servers that `cluster_manager.py` doesn't provide, so `--cluster-endpoints=` is **mandatory** there (CI starts module servers via Docker). The endpoint flags on the non-module runs are only for pointing at a server you started yourself; see the root [../AGENTS.md](../AGENTS.md) "Test Servers" section. A `valkey-server`/`redis-server` on `PATH` is required either way.

### Test Execution Options

```bash
# Run with existing endpoints
npm run test:debug -- --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379

# Run with TLS
npm run test:debug -- --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379 --tls=true

# Run specific test pattern
npm run test:debug -- --testPathIgnorePatterns=ServerModules --testNamePattern="batch"   # test:debug includes ServerModules.test.ts, which fails without --cluster-endpoints

# Run server modules tests
npm run test:modules -- --cluster-endpoints=localhost:7000
```

### Testing Connection Establishment (Avoid Count-Based CLIENT LIST Assertions)

Do not assert on `CLIENT LIST` connection _counts_ to detect whether a specific
client connected (e.g. baseline + 1). Since the socket-IPC-to-NAPI change,
`close()` only drops the JS handle and the Rust worker exits later, so
connections from a preceding test can linger in `CLIENT LIST` when the baseline
is taken and disappear one poll later, producing a net-zero delta and a flaky
test. Polling until the count "stabilizes" does not fix this: two matching
readings only prove stability for one poll window.

Instead, identify the client deterministically by a unique `clientName`
(passed in the client configuration) and assert `CLIENT LIST` excludes that name
before the first command and includes it afterwards. See the
`lazy connection establishes only on first command` test in
`tests/GlideClient.test.ts` and the pattern in `tests/NodeDiscoveryMode.test.ts`.

## Contribution Requirements

DCO signoff and Conventional Commits are required repo-wide — see [../AGENTS.md](../AGENTS.md#contribution-requirements) for the exact commands. Use the `node` scope, e.g. `feat(node): implement cluster scan with routing options`.

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

**Rust Components** (the NAPI glue — CI lints this from `node/rust-client/` via the shared Rust gate):

```bash
# From node/rust-client/ — runs the full repo-wide Rust gate (fmt --check, clippy ×2, cargo deny, cargo doc).
# See ../AGENTS.md for the exact command list.
cargo fmt --all -- --check
cargo clippy --all-features --all-targets -- -D warnings
```

## Guardrails & Policies

### Generated Outputs (Never Commit)

- `build-ts/` - Compiled TypeScript output
- `node_modules/` - Node.js dependencies
- `rust-client/node_modules/` - Rust client dependencies
- `rust-client/target/` - Rust build artifacts
- `rust-client/valkey-glide.*.node` and `build-ts/valkey-glide.*.node` - Native binaries (the build copies the addon into `build-ts/`)
- `*.tsbuildinfo` - TypeScript incremental build files
- `test-report*.html` - Test reports
- `glide-logs/` - Runtime logs
- (Lock files ARE committed and required: `npm ci` needs `package-lock.json`, and the yarn-compat tests need their `yarn.lock` via `yarn install --immutable` — do not delete them)

### Node.js-Specific Rules

- **Node.js 20+ Required:** Minimum runtime version
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
│   ├── BaseClient.ts           # Core client logic: sendCommand, drainResponses, handleResponse
│   ├── GlideClient.ts          # Standalone client
│   ├── GlideClusterClient.ts   # Cluster client
│   ├── Commands.ts             # Command builders (createGet, createSet, etc.)
│   └── index.ts                # Main exports
├── rust-client/                # Rust NAPI bindings
│   ├── src/lib.rs              # GlideClientHandle, ResponseBuffer, worker pool, NAPI exports
│   └── Cargo.toml              # Rust dependencies
├── build-ts/                   # Compiled TypeScript (generated)
├── tests/                      # Test suites
├── pm-and-types-tests/         # Package manager compatibility tests
├── package.json                # npm configuration and scripts
├── tsconfig.json               # TypeScript configuration
└── jest.config.ts              # Jest test configuration (ts-jest)
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
**Key Features:** TypeScript support, NAPI-RS native bindings, direct NAPI command calls with response buffering
**Testing:** Jest test framework, interactive REPL, package manager compatibility tests
**Platforms:** Linux (glibc/musl), macOS (Intel/Apple Silicon), Windows (x86_64)
**Dependencies:** Node.js 20+, protobufjs (connection/routing only), long, platform-specific native binaries

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Development Setup:** [DEVELOPER.md](./DEVELOPER.md)
- **Examples:** [../examples/node/](../examples/node/)
- **API Documentation:** [Valkey GLIDE Node.js docs](https://valkey.io/valkey-glide/node/)
- **Documentation:** [GLIDE Docs](https://glide.valkey.io/)
- **Test Suites:** [tests/](./tests/) directory
- **Package Manager Tests:** [pm-and-types-tests/](./pm-and-types-tests/)
- **Rust Client:** [rust-client/](./rust-client/) directory
